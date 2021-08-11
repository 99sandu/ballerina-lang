/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.projects;

import io.ballerina.projects.environment.ModuleLoadRequest;
import io.ballerina.projects.environment.PackageCache;
import io.ballerina.projects.environment.PackageLockingMode;
import io.ballerina.projects.environment.PackageResolver;
import io.ballerina.projects.environment.ProjectEnvironment;
import io.ballerina.projects.environment.ResolutionRequest;
import io.ballerina.projects.environment.ResolutionResponse;
import io.ballerina.projects.environment.ResolutionResponse.ResolutionStatus;
import io.ballerina.projects.environment.ResolutionResponseDescriptor;
import io.ballerina.projects.exceptions.InvalidBalaException;
import io.ballerina.projects.internal.DefaultDiagnosticResult;
import io.ballerina.projects.internal.DependencyVersionKind;
import io.ballerina.projects.internal.ImportModuleRequest;
import io.ballerina.projects.internal.ImportModuleResponse;
import io.ballerina.projects.internal.PackageDependencyGraphBuilder;
import io.ballerina.projects.internal.PackageDiagnostic;
import io.ballerina.projects.internal.ProjectDiagnosticErrorCode;
import io.ballerina.projects.internal.ResolutionEngine;
import io.ballerina.projects.util.ProjectUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;
import org.wso2.ballerinalang.compiler.util.Names;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves dependencies and handles version conflicts in the dependency graph.
 *
 * @since 2.0.0
 */
public class PackageResolution {
    private final PackageContext rootPackageContext;
    private final PackageCache packageCache;
    private final PackageResolver packageResolver;
    private final DependencyGraph<ResolvedPackageDependency> dependencyGraph;
    private final CompilationOptions compilationOptions;
    private final ModuleResolver moduleResolver;
    private final PackageDependencyGraphBuilder depGraphBuilder;
    private final List<Diagnostic> diagnosticList;
    private DiagnosticResult diagnosticResult;

    private List<ModuleContext> topologicallySortedModuleList;
    private Collection<ResolvedPackageDependency> dependenciesWithTransitives;

    private PackageResolution(PackageContext rootPackageContext) {
        this.rootPackageContext = rootPackageContext;
        this.diagnosticList = new ArrayList<>();
        this.compilationOptions = rootPackageContext.compilationOptions();

        ProjectEnvironment projectEnvContext = rootPackageContext.project().projectEnvironmentContext();
        this.packageResolver = projectEnvContext.getService(PackageResolver.class);
        this.packageCache = projectEnvContext.getService(PackageCache.class);

        this.depGraphBuilder = new PackageDependencyGraphBuilder(rootPackageContext.descriptor());
        this.moduleResolver = new ModuleResolver(packageResolver, rootPackageContext, depGraphBuilder);

        boolean sticky = rootPackageContext.project().buildOptions().sticky();
        dependencyGraph = buildDependencyGraph(sticky);
        DependencyResolution dependencyResolution = new DependencyResolution(
                projectEnvContext.getService(PackageCache.class), moduleResolver, dependencyGraph);
        resolveDependencies(dependencyResolution);
    }

    static PackageResolution from(PackageContext rootPackageContext) {
        return new PackageResolution(rootPackageContext);
    }

    /**
     * Returns the package dependency graph of this package.
     *
     * @return the package dependency graph of this package
     */
    public DependencyGraph<ResolvedPackageDependency> dependencyGraph() {
        return dependencyGraph;
    }

    /**
     * Returns all the dependencies of this package including it's transitive dependencies.
     *
     * @return all the dependencies of this package including it's transitive dependencies
     */
    public Collection<ResolvedPackageDependency> allDependencies() {
        if (dependenciesWithTransitives != null) {
            return dependenciesWithTransitives;
        }

        dependenciesWithTransitives = dependencyGraph.toTopologicallySortedList()
                .stream()
                // Remove root package from this list.
                .filter(resolvedPkg -> resolvedPkg.packageId() != rootPackageContext.packageId())
                .collect(Collectors.toList());
        return dependenciesWithTransitives;
    }

    /**
     * Returns the module dependency graph of a given package.
     * <p>
     * This graph contains only the modules of the given package.
     *
     * @param packageId unique instance id of the package
     * @return module dependency graph
     */
    public DependencyGraph<ModuleId> moduleDependencyGraph(PackageId packageId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PackageContext packageContext() {
        return rootPackageContext;
    }

    List<ModuleContext> topologicallySortedModuleList() {
        return topologicallySortedModuleList;
    }

    public DiagnosticResult diagnosticResult() {
        if (this.diagnosticResult == null) {
            this.diagnosticResult = new DefaultDiagnosticResult(this.diagnosticList);
        }
        return diagnosticResult;
    }

    void reportDiagnostic(String message, String diagnosticErrorCode, DiagnosticSeverity severity, Location location,
                          ModuleDescriptor moduleDescriptor) {
        var diagnosticInfo = new DiagnosticInfo(diagnosticErrorCode, message, severity);
        var diagnostic = DiagnosticFactory.createDiagnostic(diagnosticInfo, location);
        var packageDiagnostic = new PackageDiagnostic(diagnostic, moduleDescriptor, rootPackageContext.project());
        this.diagnosticList.add(packageDiagnostic);
        this.diagnosticResult = new DefaultDiagnosticResult(this.diagnosticList);
    }

    /**
     * The goal of this method is to build the complete package dependency graph of this package.
     * 1) Combine {@code ModuleLoadRequest}s of all the modules in this package.
     * 2) Filter out such requests that does not requests modules of this package.
     * 3) Create {@code PackageLoadRequest}s by incorporating the versions specified in Ballerina.toml file.
     * <p>
     * Now you have a set of PackageLoadRequests that contains all the direct dependencies of this package.
     * Load allbthese packages using the PackageResolver service. With this model PackageResolver does not
     * need to be aware of the current package. Once all the direct dependencies are loaded,
     * combine there dependency graphs into a single that contains all the transitives.
     * Now check for cycles and version conflicts. Once the version conflicts are resolved, return the graph.
     *
     * @return package dependency graph of this package
     */
    private DependencyGraph<ResolvedPackageDependency> buildDependencyGraph(boolean sticky) {
        // TODO We should get diagnostics as well. Need to design that contract
        if (rootPackageContext.project().kind() == ProjectKind.BALA_PROJECT) {
            createDependencyGraphFromBALA();
        } else {
            createDependencyGraphFromSources();
//            return createDependencyGraphFromSourcesNew(sticky);
        }

        // Once we reach this section, all the direct dependencies have been resolved
        // Here we resolve all transitive dependencies
        // TODO Check for cycles
        return depGraphBuilder.buildPackageDependencyGraph(rootPackageContext.descriptor(), packageResolver,
                packageCache, rootPackageContext.project());
    }

    private LinkedHashSet<ModuleLoadRequest> getModuleLoadRequestsOfDirectDependencies() {
        LinkedHashSet<ModuleLoadRequest> allModuleLoadRequests = new LinkedHashSet<>();
        for (ModuleId moduleId : rootPackageContext.moduleIds()) {
            ModuleContext moduleContext = rootPackageContext.moduleContext(moduleId);
            allModuleLoadRequests.addAll(moduleContext.populateModuleLoadRequests());
        }

        if (!compilationOptions.skipTests()) {
            for (ModuleId moduleId : rootPackageContext.moduleIds()) {
                ModuleContext moduleContext = rootPackageContext.moduleContext(moduleId);
                allModuleLoadRequests.addAll(moduleContext.populateTestSrcModuleLoadRequests());
            }
        }

        // TODO: Move to compiler extension once new Compiler Extension model is introduced
        if (compilationOptions.observabilityIncluded()) {
            {
                String moduleName = Names.OBSERVE.getValue();
                ModuleLoadRequest observeModuleLoadReq = new ModuleLoadRequest(
                        PackageOrg.from(Names.BALLERINA_INTERNAL_ORG.value), moduleName,
                        PackageDependencyScope.DEFAULT, DependencyResolutionType.INJECTED);
                allModuleLoadRequests.add(observeModuleLoadReq);
            }
            {
                String moduleName = Names.OBSERVE.getValue();
                ModuleLoadRequest observeModuleLoadReq = new ModuleLoadRequest(
                        PackageOrg.from(Names.BALLERINA_ORG.value), moduleName,
                        PackageDependencyScope.DEFAULT, DependencyResolutionType.INJECTED);
                allModuleLoadRequests.add(observeModuleLoadReq);
            }
        }

        // TODO Can we make this a builtin compiler plugin
        if ("k8s".equals(compilationOptions.getCloud()) || "docker".equals(compilationOptions.getCloud())) {
            String moduleName = Names.CLOUD.getValue();
            ModuleLoadRequest c2cModuleLoadReq = new ModuleLoadRequest(
                    PackageOrg.from(Names.BALLERINA_ORG.value), moduleName,
                    PackageDependencyScope.DEFAULT, DependencyResolutionType.INJECTED);
            allModuleLoadRequests.add(c2cModuleLoadReq);
        }

        return allModuleLoadRequests;
    }

    DependencyManifest.Package getVersionFromDependencyManifest(PackageOrg requestedPkgOrg,
                                                                PackageName requestedPkgName) {
        // TODO Optimize this lookup.
        // TODO Transform dependencies to multi-level map ==> Map<PackageOrg, Map<PackageName, Dependency>>
        if (rootPackageContext.dependencyManifest() != null) {
            for (DependencyManifest.Package dependency : rootPackageContext.dependencyManifest().packages()) {
                if (dependency.org().equals(requestedPkgOrg) && dependency.name().equals(requestedPkgName)) {
                    return dependency;
                }
            }
        }
        return null;
    }

    PackageManifest.LocalPackage getVersionFromPackageManifest(PackageOrg requestedPkgOrg,
                                                               PackageName requestedPkgName) {
        for (PackageManifest.LocalPackage dependency : rootPackageContext.packageManifest().localPackages()) {
            if (dependency.org().equals(requestedPkgOrg) && dependency.name().equals(requestedPkgName)) {
                return dependency;
            }
        }
        return null;
    }

    private void createDependencyGraphFromBALA() {
        DependencyGraph<PackageDescriptor> dependencyGraphStoredInBALA = rootPackageContext.dependencyGraph();
        Collection<PackageDescriptor> directDependenciesOfBALA =
                dependencyGraphStoredInBALA.getDirectDependencies(rootPackageContext.descriptor());

        // 1) Create ResolutionRequest instances for each direct dependency of the bala
        LinkedHashSet<ResolutionRequest> resolutionRequests = new LinkedHashSet<>();
        for (PackageDescriptor packageDescriptor : directDependenciesOfBALA) {
            resolutionRequests.add(ResolutionRequest.from(packageDescriptor, PackageDependencyScope.DEFAULT,
                    rootPackageContext.project().buildOptions().offlineBuild()));
        }

        // 2) Resolve direct dependencies. My assumption is that, all these dependencies comes from BALAs
        List<ResolutionResponse> resolutionResponses =
                packageResolver.resolvePackages(new ArrayList<>(resolutionRequests), rootPackageContext.project());
        for (ResolutionResponse resolutionResponse : resolutionResponses) {
            if (resolutionResponse.resolutionStatus() == ResolutionStatus.UNRESOLVED) {
                PackageDescriptor dependencyPkgDesc = resolutionResponse.packageLoadRequest().packageDescriptor();
                throw new ProjectException("Dependency cannot be found:" +
                        " org=" + dependencyPkgDesc.org() +
                        ", package=" + dependencyPkgDesc.name() +
                        ", version=" + dependencyPkgDesc.version());
            }
        }

        depGraphBuilder.mergeGraph(rootPackageContext.dependencyGraph(), PackageDependencyScope.DEFAULT,
                DependencyVersionKind.USER_SPECIFIED);
    }

    private void createDependencyGraphFromSources() {
        // 1) Get PackageLoadRequests for all the direct dependencies of this package
        LinkedHashSet<ModuleLoadRequest> moduleLoadRequests = getModuleLoadRequestsOfDirectDependencies();
        for (ModuleLoadRequest moduleLoadRequest : moduleLoadRequests) {
            PackageOrg packageOrg;
            Optional<PackageOrg> optionalOrgName = moduleLoadRequest.orgName();
            if (optionalOrgName.isEmpty()) {
                if (rootPackageContext.project().kind() == ProjectKind.SINGLE_FILE_PROJECT) {
                    // This is an invalid import in a single file project.
                    continue;
                }
                // At the moment we don't check whether the requested module is available
                // in the current package or not. This error will be reported during the SymbolEnter pass.
                packageOrg = rootPackageContext.packageOrg();
            } else {
                packageOrg = optionalOrgName.get();
            }

            ImportModuleRequest importModuleRequest = new ImportModuleRequest(packageOrg,
                    moduleLoadRequest.moduleName());
            try {
                moduleResolver.resolve(importModuleRequest, 
                                       moduleLoadRequest.scope(),
                                       moduleLoadRequest.dependencyResolvedType());
            } catch (InvalidBalaException e) {
                // TODO Temporary fix
                ModuleName moduleName = ModuleName.from(rootPackageContext.descriptor().name());
                ModuleDescriptor moduleDescriptor = ModuleDescriptor
                        .from(moduleName, rootPackageContext.descriptor());
                for (Location moduleLoadReqLocation : moduleLoadRequest.locations()) {
                    reportDiagnostic(e.getMessage(), ProjectDiagnosticErrorCode.INVALID_BALA_FILE.diagnosticId(),
                                     DiagnosticSeverity.ERROR, moduleLoadReqLocation, moduleDescriptor);
                }
            }
        }
    }

    DependencyGraph<ResolvedPackageDependency> createDependencyGraphFromSourcesNew(boolean sticky) {
        // 1) Get PackageLoadRequests for all the direct dependencies of this package
        LinkedHashSet<ModuleLoadRequest> moduleLoadRequests = getModuleLoadRequestsOfDirectDependencies();

        // Get the direct dependencies of the current package.
        // This list does not contain langlib and the root package.
        PackageContainer<DirectPackageDependency> directDepsContainer =
                moduleResolver.resolveModuleLoadRequests(moduleLoadRequests);

        // Create Resolution requests for each direct dependency
        boolean offline = rootPackageContext.project().buildOptions().offlineBuild();
        // Set the default locking mode based on the sticky build option.
        PackageLockingMode lockingMode = sticky ? PackageLockingMode.HARD : PackageLockingMode.MEDIUM;

        List<ResolutionRequest> resolutionRequests = new ArrayList<>();
        for (DirectPackageDependency directPkgDependency : directDepsContainer.getAll()) {
            PackageVersion depVersion;
            PackageDescriptor depPkgDesc = directPkgDependency.pkgDesc();
            if (directPkgDependency.dependencyKind() == DirectPackageDependencyKind.NEW) {
                depVersion = directPkgDependency.pkgDesc.version();
            } else if (directPkgDependency.dependencyKind() == DirectPackageDependencyKind.EXISTING) {
                // Use the version specified in the Dependencies.toml file.
                // Here are some additional constraints:
                // If that dependency is a direct dependency then use the version otherwise leave it.
                // The situation is that an indirect dependency(previous compilation) has become a
                // direct dependency (this compilation). Here we ignore the previous indirect dependency version and
                // look up Ballerina central repository for the latest version which is in the same compatible range.
                PackageManifest.Dependency dependency = getVersionFromPackageManifest(
                        depPkgDesc.org(), depPkgDesc.name());
                depVersion = dependency.version();
                if (dependency.isTransitive()) {
                    // The dependency is a transitive dependency.
                    // Locking mode should be SOFT irrespective of the stickiness.
                    lockingMode = PackageLockingMode.SOFT;
                }

            } else {
                throw new IllegalStateException("Unsupported direct dependency kind: " +
                        directPkgDependency.dependencyKind());
            }

            resolutionRequests.add(ResolutionRequest.from(
                    PackageDescriptor.from(depPkgDesc.org(), depPkgDesc.name(), depVersion),
                    directPkgDependency.scope, directPkgDependency.resolutionType, offline, lockingMode));
        }

        List<ResolutionResponseDescriptor> responseDescriptors =
                packageResolver.resolveDependencyVersions(resolutionRequests);

        // TODO move this to the constructor of this class
        ResolutionEngine resolutionEngine = new ResolutionEngine(rootPackageContext.project(),
                rootPackageContext.descriptor(), offline, sticky);

        for (ResolutionResponseDescriptor resolutionResponse : responseDescriptors) {
            if (resolutionResponse.resolutionStatus() == ResolutionStatus.UNRESOLVED) {
                // TODO Report diagnostics
                continue;
            }
            PackageDescriptor pkgDesc = resolutionResponse.resolvedDescriptor();
            // Following error cannot happen
            DirectPackageDependency directPkgDep = directDepsContainer.get(pkgDesc.org(), pkgDesc.name())
                    .orElseThrow(() -> new IllegalStateException("The package `" + pkgDesc +
                            "` should be part of the direct dependency list"));
            resolutionEngine.addDirectDependency(pkgDesc, directPkgDep.scope(), directPkgDep.resolutionType());
        }

        for (ResolutionResponseDescriptor resolutionResponse : responseDescriptors) {
            if (resolutionResponse.resolutionStatus() == ResolutionStatus.UNRESOLVED) {
                // TODO Report diagnostics
                continue;
            }
            resolutionEngine.addTransitiveDependencies(resolutionResponse.dependencyGraph().get());
        }

        return resolutionEngine.resolveDependencies();

    }

    static Optional<ModuleContext> findModuleInPackage(PackageContext resolvedPackage, String moduleNameStr) {
        PackageName packageName = resolvedPackage.packageName();
        ModuleName moduleName;
        if (packageName.value().equals(moduleNameStr)) {
            moduleName = ModuleName.from(packageName);
        } else {
            String moduleNamePart = moduleNameStr.substring(packageName.value().length() + 1);
            if (moduleNamePart.isEmpty()) {
                moduleNamePart = null;
            }
            moduleName = ModuleName.from(packageName, moduleNamePart);
        }
        ModuleContext resolvedModule = resolvedPackage.moduleContext(moduleName);
        if (resolvedModule == null) {
            return Optional.empty();
        }

        // TODO convert this to a debug log
        return Optional.of(resolvedModule);
    }

    /**
     * Resolve dependencies of each package, which in turn resolves dependencies of each module.
     * <p>
     * This logic should get packages from the dependency graph, not from the PackageCache.
     * Because PackageCache may contain various versions of a single package,
     * but the dependency graph contains only the resolved version.
     */
    private void resolveDependencies(DependencyResolution dependencyResolution) {
        // Topologically sort packages in the package dependency graph.
        // Iterate through the sorted package list
        // Resolve each package
        // Get the module dependency graph of the package.
        // This graph should only contain the modules in that particular package.
        // Topologically sort the module dependency graph.
        // Iterate through the sorted module list.
        // Compile the module and collect diagnostics.
        // Repeat this for each module in each package in the package dependency graph.
        List<ModuleContext> sortedModuleList = new ArrayList<>();
        List<ResolvedPackageDependency> sortedPackages = dependencyGraph.toTopologicallySortedList();
        for (ResolvedPackageDependency pkgDependency : sortedPackages) {
            Package resolvedPackage = pkgDependency.packageInstance();
            resolvedPackage.packageContext().resolveDependencies(dependencyResolution);
            DependencyGraph<ModuleId> moduleDependencyGraph = resolvedPackage.moduleDependencyGraph();
            List<ModuleId> sortedModuleIds = moduleDependencyGraph.toTopologicallySortedList();
            for (ModuleId moduleId : sortedModuleIds) {
                ModuleContext moduleContext = resolvedPackage.module(moduleId).moduleContext();
                sortedModuleList.add(moduleContext);
            }
        }
        this.topologicallySortedModuleList = Collections.unmodifiableList(sortedModuleList);
    }

    /**
     * This entity is used by packages and modules to resolve their dependencies from the dependency graph.
     *
     * @since 2.0.0
     */
    static class DependencyResolution {
        private final PackageCache delegate;
        private final ModuleResolver moduleResolver;
        private final DependencyGraph<ResolvedPackageDependency> dependencyGraph;

        private DependencyResolution(PackageCache delegate,
                                     ModuleResolver moduleResolver,
                                     DependencyGraph<ResolvedPackageDependency> dependencyGraph) {
            this.delegate = delegate;
            this.moduleResolver = moduleResolver;
            this.dependencyGraph = dependencyGraph;
        }

        public Optional<Package> getPackage(PackageOrg packageOrg, PackageName packageName) {
            List<Package> resolvedPackages = delegate.getPackages(packageOrg, packageName);
            for (Package resolvedPackage : resolvedPackages) {
                if (containsPackage(resolvedPackage)) {
                    return Optional.of(resolvedPackage);
                }
            }

            // TODO convert this to a debug log
            return Optional.empty();
        }

        public Optional<Module> getModule(PackageOrg packageOrg, PackageName packageName, ModuleName moduleName) {
            Optional<Package> resolvedPkg = getPackage(packageOrg, packageName);
            if (resolvedPkg.isEmpty()) {
                return Optional.empty();
            }

            Module resolvedModule = resolvedPkg.get().module(moduleName);
            if (resolvedModule == null) {
                return Optional.empty();
            }

            // TODO convert this to a debug log
            return Optional.of(resolvedModule);
        }

        public Optional<ModuleContext> getModule(PackageOrg packageOrg, String moduleNameStr) {
            ImportModuleRequest importModuleRequest = new ImportModuleRequest(packageOrg, moduleNameStr);
            ImportModuleResponse importModuleResponse = moduleResolver.getImportModuleResponse(importModuleRequest);
            if (importModuleResponse == null) {
                return Optional.empty();
            }

            PackageName packageName;
            // TODO remove the null check and else block once the new resolution is fully done
            packageName = importModuleResponse.packageDescriptor().name();

            Optional<Package> optionalPackage = getPackage(packageOrg,
                                                           packageName);
            if (optionalPackage.isEmpty()) {
                // This branch cannot be executed since the package is resolved before hand
                throw new IllegalStateException("Cannot find the resolved package for org: " + packageOrg +
                        " name: " + packageName);
            }

            return PackageResolution.findModuleInPackage(optionalPackage.get().packageContext(), moduleNameStr);
        }

        private boolean containsPackage(Package pkg) {
            for (ResolvedPackageDependency graphNode : dependencyGraph.getNodes()) {
                if (graphNode.packageId() == pkg.packageId()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Find packages that contain imported modules.
     *
     * @since 2.0.0
     */
    private class ModuleResolver {
        private final Map<ImportModuleRequest, ImportModuleResponse> responseMap = new HashMap<>();
        private final PackageDependencyGraphBuilder depGraphBuilder;
        private final PackageResolver packageResolver;
        private final PackageContext rootPkgContext;

        private ModuleResolver(PackageResolver packageResolver,
                               PackageContext rootPkgContext,
                               PackageDependencyGraphBuilder depGraphBuilder) {
            this.packageResolver = packageResolver;
            this.rootPkgContext = rootPkgContext;
            this.depGraphBuilder = depGraphBuilder;
        }

        ImportModuleResponse getImportModuleResponse(ImportModuleRequest importModuleRequest) {
            return responseMap.get(importModuleRequest);
        }

        PackageOrg getPackageOrg(ModuleLoadRequest moduleLoadRequest) {
            Optional<PackageOrg> optionalOrgName = moduleLoadRequest.orgName();
            return optionalOrgName.orElseGet(rootPackageContext::packageOrg);
        }

        /**
         * Resolves the given list of module names to Packages and returns that list.
         * <p>
         * The returned package list does not contain langlib packages and the root package.
         */
        PackageContainer<DirectPackageDependency> resolveModuleLoadRequests(
                Collection<ModuleLoadRequest> moduleLoadRequests) {
            PackageContainer<DirectPackageDependency> pkgContainer = new PackageContainer<>();
            List<ImportModuleRequest> unresolvedModuleRequests = new ArrayList<>();
            for (ModuleLoadRequest moduleLoadRequest : moduleLoadRequests) {
                resolveModuleLoadRequest(moduleLoadRequest, pkgContainer, unresolvedModuleRequests);
            }

            // Resolve unresolved import module declarations
            List<ImportModuleResponse> importModResponses =
                    packageResolver.resolvePackageNames(unresolvedModuleRequests);
            for (ImportModuleResponse importModResp : importModResponses) {
                if (importModResp.resolutionStatus() == ResolutionStatus.UNRESOLVED) {
                    // TODO Report diagnostics
                    // TODO Require a proper package.properties file.
                    continue;
                }

                DirectPackageDependency newPkgDep;
                ImportModuleRequest importModuleRequest = importModResp.importModuleRequest();
                PackageDescriptor pkgDesc = importModResp.packageDescriptor();
                Optional<DirectPackageDependency> pkgDepOptional = pkgContainer.get(pkgDesc.org(), pkgDesc.name());
                ModuleLoadRequest moduleLoadRequest = importModuleRequest.moduleLoadRequest();
                if (pkgDepOptional.isEmpty()) {
                    newPkgDep = new DirectPackageDependency(pkgDesc,
                            DirectPackageDependencyKind.NEW,
                            moduleLoadRequest.scope(),
                            moduleLoadRequest.dependencyResolvedType());
                } else {
                    DirectPackageDependency currentPkgDep = pkgDepOptional.get();
                    // Do not override the scope, if the current scope is PackageDependencyScope.DEFAULT,
                    PackageDependencyScope scope =
                            currentPkgDep.scope() == PackageDependencyScope.DEFAULT ?
                                    PackageDependencyScope.DEFAULT :
                                    moduleLoadRequest.scope();

                    // Do not override the resolutionType,
                    // if the current resolutionType is DependencyResolutionType.SOURCE ,
                    DependencyResolutionType resolutionType =
                            currentPkgDep.resolutionType() == DependencyResolutionType.SOURCE ?
                                    DependencyResolutionType.SOURCE :
                                    moduleLoadRequest.dependencyResolvedType();
                    newPkgDep = new DirectPackageDependency(pkgDesc,
                            DirectPackageDependencyKind.NEW, scope, resolutionType);
                }
                pkgContainer.add(pkgDesc.org(), pkgDesc.name(), newPkgDep);
                responseMap.put(importModuleRequest, importModResp);
            }
            return pkgContainer;
        }

        private void resolveModuleLoadRequest(ModuleLoadRequest moduleLoadRequest,
                                              PackageContainer<DirectPackageDependency> pkgContainer,
                                              List<ImportModuleRequest> unresolvedModuleRequests) {
            PackageDescriptor pkgDesc;
            PackageOrg pkgOrg = getPackageOrg(moduleLoadRequest);
            String moduleName = moduleLoadRequest.moduleName();
            Collection<PackageName> possiblePkgNames = ProjectUtils.getPossiblePackageNames(
                    pkgOrg, moduleLoadRequest.moduleName());
            if (ProjectUtils.isBuiltInPackage(pkgOrg, moduleName)) {
                pkgDesc = PackageDescriptor.from(pkgOrg, PackageName.from(moduleName));
            } else {
                if (possiblePkgNames.size() == 1) {
                    // This is not a hierarchical module pkgName,
                    // hence the package pkgName is same as the module pkgName
                    pkgDesc = PackageDescriptor.from(pkgOrg, PackageName.from(moduleName));
                } else {
                    // This is a hierarchical module pkgName
                    // This method returns a non-null pkgDesc if and only if that package contains the given module.
                    pkgDesc = findHierarchicalModule(moduleName, pkgOrg, possiblePkgNames);
                }
            }

            if (pkgDesc == null) {
                // TODO How can use use possiblePackages?
                List<PackageDescriptor> possiblePackages = Collections.emptyList();
                ImportModuleRequest importModuleRequest = new ImportModuleRequest(
                        pkgOrg, moduleLoadRequest, possiblePackages);
                unresolvedModuleRequests.add(importModuleRequest);
                return;
            }

            PackageName pkgName = pkgDesc.name();
            if (isRootPackage(pkgOrg, pkgName)) {
                // Do not add the root package to the dependencies list
                return;
            }

            Optional<DirectPackageDependency> pkgDepOptional = pkgContainer.get(pkgOrg, pkgName);
            if (pkgDepOptional.isEmpty()) {
                PackageManifest.Dependency versionFromPackageManifest =
                        getVersionFromPackageManifest(pkgDesc.org(), pkgDesc.name());
                DirectPackageDependencyKind dependencyKind;
                if (versionFromPackageManifest == null) {
                    dependencyKind = DirectPackageDependencyKind.NEW;
                } else {
                    dependencyKind = DirectPackageDependencyKind.EXISTING;
                }
                pkgContainer.add(pkgOrg, pkgName, new DirectPackageDependency(pkgDesc,
                        dependencyKind, moduleLoadRequest.scope(),
                        moduleLoadRequest.dependencyResolvedType()));
            } else {
                // There exists a direct dependency in the container
                DirectPackageDependency currentPkgDep = pkgDepOptional.get();

                // Use the current resolutionType only if it is DependencyResolutionType.SOURCE,
                //  Override it otherwise.
                DependencyResolutionType resolutionType =
                        currentPkgDep.resolutionType() == DependencyResolutionType.SOURCE ?
                                DependencyResolutionType.SOURCE :
                                moduleLoadRequest.dependencyResolvedType();

                // Use the current scope only if it is PackageDependencyScope.DEFAULT,
                //  Override it otherwise.
                PackageDependencyScope scope =
                        currentPkgDep.scope() == PackageDependencyScope.DEFAULT ?
                                PackageDependencyScope.DEFAULT :
                                moduleLoadRequest.scope();

                pkgContainer.add(pkgOrg, pkgName, new DirectPackageDependency(pkgDesc,
                        DirectPackageDependencyKind.EXISTING, scope, resolutionType));
            }

            ImportModuleRequest importModuleRequest = new ImportModuleRequest(pkgOrg, moduleLoadRequest);
            responseMap.put(importModuleRequest, new ImportModuleResponse(
                    PackageDescriptor.from(pkgOrg, pkgName), importModuleRequest));
        }

        private PackageDescriptor findHierarchicalModule(String moduleName,
                                                         PackageOrg packageOrg,
                                                         Collection<PackageName> possiblePkgNames) {
            for (PackageName possiblePkgName : possiblePkgNames) {
                PackageDescriptor pkgDesc = findHierarchicalModule(moduleName, packageOrg, possiblePkgName);
                if (pkgDesc != null) {
                    return pkgDesc;
                }
            }

            return null;
        }

        /**
         * Find the given module name in the dependencies.toml file recorded during the previous compilation.
         *
         * @param moduleName  Module name to be found
         * @param packageOrg  organization name
         * @param packageName Possible package name
         * @return PackageDescriptor or null
         */
        private PackageDescriptor findHierarchicalModule(String moduleName,
                                                         PackageOrg packageOrg,
                                                         PackageName packageName) {
            PackageDescriptor pkgDesc = findModuleInRootPackage(moduleName, packageOrg, packageName);
            if (pkgDesc != null) {
                return pkgDesc;
            }

            return findModuleInDependenciesToml(moduleName, packageOrg, packageName);
        }

        private PackageDescriptor findModuleInRootPackage(String moduleName,
                                                          PackageOrg packageOrg,
                                                          PackageName packageName) {
            if (packageOrg.equals(rootPackageContext.packageOrg()) &&
                    packageName.equals(rootPackageContext.packageName())) {
                Optional<ModuleContext> moduleInPackage = PackageResolution.findModuleInPackage(
                        rootPackageContext, moduleName);
                if (moduleInPackage.isPresent()) {
                    return rootPackageContext.descriptor();
                }
                // There is no such module in root package
            }
            return null;
        }

        private boolean isRootPackage(PackageOrg pkgOrg, PackageName pkgName) {
            return pkgOrg.equals(rootPackageContext.packageOrg()) &&
                    pkgName.equals(rootPackageContext.packageName());
        }

        private PackageDescriptor findModuleInDependenciesToml(String moduleName,
                                                               PackageOrg packageOrg,
                                                               PackageName packageName) {
            // Check whether this package is already defined in the package manifest, if so get the version
            PackageManifest.Dependency dependency = PackageResolution.this.getVersionFromPackageManifest(
                    packageOrg, packageName);
            if (dependency == null) {
                return null;
            }

            List<String> modules = dependency.modules().stream().map(PackageManifest.DependencyModule::moduleName)
                    .collect(Collectors.toList());
            if (modules.contains(moduleName)) {
                return PackageDescriptor.from(packageOrg, packageName);
            }
            return null;
        }

        void resolve(ImportModuleRequest importModuleRequest, PackageDependencyScope scope,
                     DependencyResolutionType dependencyResolvedType) {
            ImportModuleResponse importModuleResponse = responseMap.get(importModuleRequest);
            if (importModuleResponse != null) {
                return;
            }

            PackageOrg packageOrg = importModuleRequest.packageOrg();
            List<PackageName> possiblePkgNames = ProjectUtils.getPossiblePackageNames(importModuleRequest.packageOrg(),
                    importModuleRequest.moduleName());
            for (PackageName possiblePkgName : possiblePkgNames) {
                if (packageOrg.equals(rootPackageContext.packageOrg()) &&
                        possiblePkgName.equals(rootPackageContext.packageName())) {
                    Optional<ModuleContext> moduleInPackage = PackageResolution.findModuleInPackage(
                            rootPackageContext, importModuleRequest.moduleName());
                    if (moduleInPackage.isEmpty()) {
                        // There is no such module in this package
                        // Continue to the next package
                        continue;
                    }
                } else {
                    PackageVersion packageVersion = null;
                    String repository = null;
                    // Check whether this package is already defined as a local dependency, if so get the version
                    PackageManifest.LocalPackage localDependency = PackageResolution.this
                            .getVersionFromPackageManifest(packageOrg, possiblePkgName);
                    if (localDependency != null) {
                        packageVersion = localDependency.version();
                        repository = localDependency.repository();
                    }
                    // Check whether this package is already defined in the dependency manifest, if so get the version
                    DependencyManifest.Package dependency = PackageResolution.this.getVersionFromDependencyManifest(
                            packageOrg, possiblePkgName);
                    if (dependency != null) {
                        packageVersion = dependency.version();
                    }
                    DependencyVersionKind dependencyVersionKind = packageVersion != null ?
                            DependencyVersionKind.USER_SPECIFIED : DependencyVersionKind.LATEST;

                    // Try to resolve the package via repositories
                    PackageDescriptor pkgDesc = PackageDescriptor.from(
                            packageOrg, possiblePkgName, packageVersion, repository);
                    ResolutionResponse resolutionResponse = resolvePackage(pkgDesc, scope);
                    if (resolutionResponse.resolutionStatus() == ResolutionStatus.UNRESOLVED) {
                        // There is no such package exists
                        // Continue to the next possible package name
                        continue;
                    }

                    Package resolvedPackage = resolutionResponse.resolvedPackage();
                    Optional<ModuleContext> moduleInPackage = PackageResolution.findModuleInPackage(
                            resolvedPackage.packageContext(), importModuleRequest.moduleName());
                    if (moduleInPackage.isEmpty()) {
                        // There is no such module in this package
                        // Continue to the next package
                        continue;
                    }

                    // The requested module is available in the resolvedPackage
                    // Let's add it to the dependency graph.dependencyResolvedType
                    addPackageToDependencyGraph(resolutionResponse, dependencyResolvedType, dependencyVersionKind);
                }
                responseMap.put(importModuleRequest, new ImportModuleResponse(
                        PackageDescriptor.from(packageOrg, possiblePkgName), importModuleRequest));
                return;
            }

            // 3) Imported module cannot be resolved. Ignore the error for now.
            // This will be caught at a different level
        }

        private ResolutionResponse resolvePackage(PackageDescriptor pkgDesc, PackageDependencyScope scope) {
            ResolutionRequest resolutionRequest = ResolutionRequest
                    .from(pkgDesc, scope, rootPkgContext.project().buildOptions().offlineBuild());
            return packageResolver.resolvePackages(
                    List.of(resolutionRequest), rootPkgContext.project()).get(0);
        }

        private void addPackageToDependencyGraph(ResolutionResponse resolutionResponse,
                                                 DependencyResolutionType dependencyResolvedType,
                                                 DependencyVersionKind dependencyVersionKind) {
            // Adding the resolved package to the graph and merge its dependencies
            Package resolvedPackage = resolutionResponse.resolvedPackage();
            ResolutionRequest resolutionRequest = resolutionResponse.packageLoadRequest();
            if (resolutionRequest.scope() == PackageDependencyScope.DEFAULT) {
                depGraphBuilder.addDependency(rootPkgContext.descriptor(), resolvedPackage.descriptor(),
                        PackageDependencyScope.DEFAULT, dependencyResolvedType, dependencyVersionKind);

                // Merge direct dependency's dependency graph with the current one.
                depGraphBuilder.mergeGraph(resolvedPackage.packageContext().dependencyGraph(),
                        PackageDependencyScope.DEFAULT, dependencyVersionKind);
            } else if (resolutionRequest.scope() == PackageDependencyScope.TEST_ONLY) {
                depGraphBuilder.addDependency(rootPkgContext.descriptor(), resolvedPackage.descriptor(),
                        PackageDependencyScope.TEST_ONLY, dependencyResolvedType, dependencyVersionKind);

                // Merge direct dependency's dependency graph with the current one.
                depGraphBuilder.mergeGraph(resolvedPackage.packageContext().dependencyGraph(),
                        PackageDependencyScope.TEST_ONLY, dependencyVersionKind);
            }
        }
    }

    private static class DirectPackageDependency {
        private final PackageDescriptor pkgDesc;
        private final DirectPackageDependencyKind dependencyKind;
        private final PackageDependencyScope scope;
        private final DependencyResolutionType resolutionType;

        public DirectPackageDependency(PackageDescriptor pkgDesc,
                                       DirectPackageDependencyKind dependencyKind,
                                       PackageDependencyScope scope,
                                       DependencyResolutionType resolutionType) {
            this.pkgDesc = pkgDesc;
            this.dependencyKind = dependencyKind;
            this.scope = scope;
            this.resolutionType = resolutionType;
        }

        public PackageDescriptor pkgDesc() {
            return pkgDesc;
        }

        public DirectPackageDependencyKind dependencyKind() {
            return dependencyKind;
        }

        public PackageDependencyScope scope() {
            return scope;
        }

        public DependencyResolutionType resolutionType() {
            return resolutionType;
        }
    }

    private enum DirectPackageDependencyKind {
        /**
         * A dependency already recorded in Dependencies.toml.
         */
        EXISTING,
        /**
         * A new package dependency introduced via a new import declaration.
         */
        NEW
    }

    private static class PackageContainer<T> {
        private final Map<PackageOrg, Map<PackageName, T>> pkgOrgMap;

        public PackageContainer() {
            this.pkgOrgMap = new HashMap<>();
        }

        void add(PackageOrg pkgOrg, PackageName pkgName, T t) {
            Map<PackageName, T> pkgNameMap = pkgOrgMap.computeIfAbsent(pkgOrg, orgName -> new HashMap<>());
            pkgNameMap.put(pkgName, t);
            pkgOrgMap.put(pkgOrg, pkgNameMap);
        }

        Optional<T> get(PackageOrg pkgOrg, PackageName pkgName) {
            Map<PackageName, T> pkgNameMap = pkgOrgMap.get(pkgOrg);
            if (pkgNameMap == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(pkgNameMap.get(pkgName));
        }

        Collection<T> getAll() {
            return pkgOrgMap.values()
                    .stream()
                    .map(Map::values)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }
    }
}
