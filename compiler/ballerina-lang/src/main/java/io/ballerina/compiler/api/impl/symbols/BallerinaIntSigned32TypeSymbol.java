/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ballerina.compiler.api.impl.symbols;

import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.symbols.IntSigned32TypeSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import org.wso2.ballerinalang.compiler.semantics.model.types.BIntSubType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;

/**
 * Represents the int:Signed32 type descriptor.
 *
 * @since 2.0.0
 */
public class BallerinaIntSigned32TypeSymbol extends AbstractTypeSymbol implements IntSigned32TypeSymbol {

    public BallerinaIntSigned32TypeSymbol(CompilerContext context, ModuleID moduleID, BIntSubType signed32Type) {
        super(context, TypeDescKind.INT, moduleID, signed32Type);
    }

    @Override
    public String name() {
        return Names.SIGNED32.value;
    }

    @Override
    public String signature() {
        return "Signed32";
    }
}
