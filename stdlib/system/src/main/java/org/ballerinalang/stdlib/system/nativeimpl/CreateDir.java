/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.stdlib.system.nativeimpl;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.stdlib.system.utils.SystemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.ballerinalang.stdlib.system.utils.SystemUtils.getBallerinaError;

/**
 * Extern function ballerina.system:createDir.
 *
 * @since 0.995.0
 */
@BallerinaFunction(
        orgName = SystemConstants.ORG_NAME,
        packageName = SystemConstants.PACKAGE_NAME,
        functionName = "createDir",
        isPublic = true
)
public class CreateDir extends BlockingNativeCallableUnit {
    private static final Logger log = LoggerFactory.getLogger(CreateDir.class);

    @Override
    public void execute(Context context) {
        String inputPath = context.getStringArgument(0);
        boolean parentDirs = context.getBooleanArgument(0);
        try {
            Path dirPath;
            if (parentDirs) {
                dirPath = Files.createDirectories(Paths.get(inputPath));
            } else {
                dirPath = Files.createDirectory(Paths.get(inputPath));
            }
            context.setReturnValues(new BString(dirPath.toAbsolutePath().toString()));
        } catch (FileAlreadyExistsException e) {
            String msg = "File already exists. Failed to create the file: " + inputPath;
            log.error(msg, e);
            context.setReturnValues(getBallerinaError("INVALID_OPERATION", msg));
        } catch (SecurityException e) {
            String msg = "Permission denied. Failed to create the file: " + inputPath;
            log.error(msg, e);
            context.setReturnValues(getBallerinaError("PERMISSION_ERROR", msg));
        } catch (IOException e) {
            log.error("IO error while creating the file " + inputPath, e);
            context.setReturnValues(getBallerinaError("FILE_SYSTEM_ERROR", e));
        } catch (Exception e) {
            log.error("Error while creating the file " + inputPath, e);
            context.setReturnValues(getBallerinaError("FILE_SYSTEM_ERROR", e));
        }
    }
}
