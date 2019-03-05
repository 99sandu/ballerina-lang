/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.ballerinalang.test.types.constant;

import org.ballerinalang.launcher.util.BCompileUtil;
import org.ballerinalang.launcher.util.BRunUtil;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.model.values.BBoolean;
import org.ballerinalang.model.values.BByte;
import org.ballerinalang.model.values.BDecimal;
import org.ballerinalang.model.values.BFloat;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.exceptions.BLangRuntimeException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Constant test cases.
 */
public class MapConstantTest {

    private static CompileResult compileResult;

    @BeforeClass
    public void setup() {
        compileResult = BCompileUtil.compile("test-src/types/constant/map-literal-constant.bal");
    }

    @Test
    public void testSimpleBooleanConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testSimpleBooleanConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key1\":true}");
    }

    @Test
    public void testComplexBooleanConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexBooleanConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key2\":{\"key1\":true}}");
    }

    @Test
    public void testSimpleIntConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testSimpleIntConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key1\":1}");
    }

    @Test
    public void testComplexIntConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexIntConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key2\":{\"key1\":1}}");
    }

    @Test
    public void testSimpleByteConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testSimpleByteConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key1\":10}");
    }

    @Test
    public void testComplexByteConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexByteConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key2\":{\"key1\":10}}");
    }

    @Test
    public void testSimpleDecimalConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testSimpleDecimalConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key1\":100}");
    }

    @Test
    public void testComplexDecimalConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexDecimalConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key2\":{\"key1\":100}}");
    }

    @Test
    public void testSimpleFloatConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testSimpleFloatConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key1\":2.0}");
    }

    @Test
    public void testComplexFloatConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexFloatConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key2\":{\"key1\":2.0}}");
    }

    @Test
    public void testSimpleStringConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testSimpleStringConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key1\":\"value1\"}");
    }

    @Test
    public void testComplexStringConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexStringConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"key2\":{\"key1\":\"value1\"}}");
    }

    @Test
    public void testComplexConstMap() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testComplexConstMap");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"k3\":{\"k2\":{\"k1\":\"v1\"}}}");
    }

    @Test
    public void testConstInAnnotations() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testConstInAnnotations");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{name:\"testAnnotation\", moduleName:\"\", moduleVersion:\"\"," +
                " value:{s:\"Ballerina\", i:100, m:{\"mKey\":\"mValue\"}}}");
    }

    @Test
    public void getNestedConstantMapValue() {
        BValue[] returns = BRunUtil.invoke(compileResult, "getNestedConstantMapValue");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "m5v");
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
            expectedExceptionsMessageRegExp = ".*modification not allowed on frozen value.*")
    public void updateNestedConstantMapValue() {
        BRunUtil.invoke(compileResult, "updateNestedConstantMapValue");
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
            expectedExceptionsMessageRegExp = ".*modification not allowed on frozen value.*")
    public void updateNestedConstantMapValue2() {
        BRunUtil.invoke(compileResult, "updateNestedConstantMapValue2");
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
            expectedExceptionsMessageRegExp = ".*modification not allowed on frozen value.*")
    public void updateConstantMapValueInArray() {
        BRunUtil.invoke(compileResult, "updateConstantMapValueInArray");
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
            expectedExceptionsMessageRegExp = ".*modification not allowed on frozen value.*")
    public void updateConstantMapValueInArray2() {
        BRunUtil.invoke(compileResult, "updateConstantMapValueInArray2");
    }

    @Test
    public void getConstantMapValueInArray() {
        BValue[] returns = BRunUtil.invoke(compileResult, "getConstantMapValueInArray");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "m5v");
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
            expectedExceptionsMessageRegExp = ".*modification not allowed on frozen value.*")
    public void updateReturnedConstantMap() {
        BRunUtil.invoke(compileResult, "updateReturnedConstantMap");
    }

    @Test(expectedExceptions = BLangRuntimeException.class,
            expectedExceptionsMessageRegExp = ".*modification not allowed on frozen value.*")
    public void updateReturnedConstantMap2() {
        BRunUtil.invoke(compileResult, "updateReturnedConstantMap2");
    }

    @Test
    public void testBooleanConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testBooleanConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"bm4kn\":true}");
    }

    @Test
    public void testIntConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testIntConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"im4kn\":123}");
    }

    @Test
    public void testByteConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testByteConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"bytem4kn\":64}");
    }

    @Test
    public void testFloatConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testFloatConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"fm4kn\":12.5}");
    }

    @Test
    public void testDecimalConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testDecimalConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"dm4kn\":5.56}");
    }

    @Test
    public void testStringConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testStringConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"sm4kn\":\"sm3v\"}");
    }

    @Test
    public void testNullConstKeyReference() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testNullConstKeyReference");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{\"nm4kn\":()}");
    }

    @Test
    public void testBooleanConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testBooleanConstKeyReferenceInLocalVar");
        Assert.assertNotNull(returns[0]);
        Assert.assertTrue(((BBoolean) returns[0]).booleanValue());
    }

    @Test
    public void testIntConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testIntConstKeyReferenceInLocalVar");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 123);
    }

    @Test
    public void testByteConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testByteConstKeyReferenceInLocalVar");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(((BByte) returns[0]).intValue(), 64);
    }

    @Test
    public void testFloatConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testFloatConstKeyReferenceInLocalVar");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(((BFloat) returns[0]).floatValue(), 12.5);
    }

    @Test
    public void testDecimalConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testDecimalConstKeyReferenceInLocalVar");
        Assert.assertNotNull(returns[0]);
        BigDecimal expected = new BigDecimal("5.56", MathContext.DECIMAL128);
        Assert.assertEquals(((BDecimal) returns[0]).decimalValue().compareTo(expected), 0);
    }

    @Test
    public void testStringConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testStringConstKeyReferenceInLocalVar");
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "sm3v");
    }

    @Test
    public void testNullConstKeyReferenceInLocalVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testNullConstKeyReferenceInLocalVar");
        Assert.assertNull(returns[0]);
    }
}
