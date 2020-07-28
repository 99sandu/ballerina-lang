// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
import ballerina/lang.'string as strings;
import ballerina/lang.'xml;

public function createPipeline(
        (Type)[]|map<Type>|record{}|string|xml|table<map<Type>>|stream<Type, error?>|_Iterable collection,
        typedesc<Type> resType)
            returns _StreamPipeline {
    return new _StreamPipeline(collection, resType);
}

public function createInputFunction(function(_Frame _frame) returns _Frame|error? inputFunc)
        returns _StreamFunction {
    return new _InputFunction(inputFunc);
}

public function createNestedFromFunction(function(_Frame _frame) returns any|error? collectionFunc)
        returns _StreamFunction {
    return new _NestedFromFunction(collectionFunc);
}

public function createLetFunction(function(_Frame _frame) returns _Frame|error? letFunc)
        returns _StreamFunction {
    return new _LetFunction(letFunc);
}

public function createInnerJoinFunction(_StreamPipeline joinedPipeline,
                                        function(_Frame _frame) returns boolean onCondition)
        returns _StreamFunction {
    return new _InnerJoinFunction(joinedPipeline, onCondition);
}

public function createOuterJoinFunction(_StreamPipeline joinedPipeline,
                                        function(_Frame _frame) returns boolean onCondition)
        returns _StreamFunction {
    return new _OuterJoinFunction(joinedPipeline, onCondition);
}

public function createFilterFunction(function(_Frame _frame) returns boolean filterFunc)
        returns _StreamFunction {
    return new _FilterFunction(filterFunc);
}

public function createSelectFunction(function(_Frame _frame) returns _Frame|error? selectFunc)
        returns _StreamFunction {
    return new _SelectFunction(selectFunc);
}

public function createDoFunction(function(_Frame _frame) doFunc) returns _StreamFunction {
    return new _DoFunction(doFunc);
}

public function createLimitFunction(int lmt) returns _StreamFunction {
    return new _LimitFunction(lmt);
}

public function addStreamFunction(@tainted _StreamPipeline pipeline, @tainted _StreamFunction streamFunction) {
    pipeline.addStreamFunction(streamFunction);
}

public function getStreamFromPipeline(_StreamPipeline pipeline) returns stream<Type, error?> {
    return pipeline.getStream();
}

public function createOrderByFunction(string[] fieldNames, boolean[] sortTypes, stream<Type, error?> strm,
@tainted Type[] arr) returns stream<Type, error?> {
    record {| Type value; |}|error? v = strm.next();
    while (v is record {| Type value; |}) {
        arr.push(v.value);
        v = strm.next();
    }

    StreamOrderBy streamOrderByObj = new StreamOrderBy(fieldNames, sortTypes);
    var sortedArr = <@untainted>streamOrderByObj.topDownMergeSort(arr);

    return sortedArr.toStream();
}

public function toArray(stream<Type, error?> strm, Type[] arr) returns Type[]|error {
    record {| Type value; |}|error? v = strm.next();
    while (v is record {| Type value; |}) {
        arr.push(v.value);
        v = strm.next();
    }
    if (v is error) {
        return v;
    }
    return arr;
}

public function toXML(stream<Type, error?> strm) returns xml {
    xml result = 'xml:concat();
    record {| Type value; |}|error? v = strm.next();
    while (v is record {| Type value; |}) {
        Type value = v.value;
        if (value is xml) {
            result = result + value;
        }
        v = strm.next();
    }
    return result;
}

public function toString(stream<Type, error?> strm) returns string {
    string result = "";
    record {| Type value; |}|error? v = strm.next();
    while (v is record {| Type value; |}) {
        Type value = v.value;
        if (value is string) {
            result += value;
        }
        v = strm.next();
    }
    return result;
}

public function addToTable(stream<Type, error?> strm, table<map<Type>> tbl, error? err) returns table<map<Type>>|error {
    record {| Type value; |}|error? v = strm.next();
    while (v is record {| Type value; |}) {
        error? e = trap tbl.add(<map<Type>> v.value);
        if (e is error) {
            if (err is error) {
                return err;
            }
            return e;
        }
        v = strm.next();
    }
    if (v is error) {
        return v;
    }
    return tbl;
}

public function consumeStream(stream<Type, error?> strm) returns error? {
    any|error? v = strm.next();
    while (!(v is () || v is error)) {
        v = strm.next();
    }
    if (v is error) {
        return v;
    }
}

public type StreamOrderBy object {
    public string[] sortFields;
    public boolean[] sortTypes;

    public function init(string[] sortFields, boolean[] sortTypes) {
        self.sortFields = sortFields;
        self.sortTypes = sortTypes;
    }

    public function topDownMergeSort(@tainted Type[] events) returns @tainted Type[]{
        int index = 0;
        int n = events.length();
        Type[] b = [];
        while (index < n) {
            b[index] = events[index];
            index += 1;
        }
        self.topDownSplitMerge(b, 0, n, events);
        return events;
    }

    function topDownSplitMerge(@tainted Type[] b, int iBegin, int iEnd, @tainted Type[] a) {
        if (iEnd - iBegin < 2) {
            return;
        }
        int iMiddle = (iEnd + iBegin) / 2;
        self.topDownSplitMerge(a, iBegin, iMiddle, b);
        self.topDownSplitMerge(a, iMiddle, iEnd, b);
        self.topDownMerge(b, iBegin, iMiddle, iEnd, a);
    }

    function topDownMerge(@tainted Type[] a, int iBegin, int iMiddle, int iEnd, @tainted Type[] b) {
        int i = iBegin;
        int j = iMiddle;

        int k = iBegin;
        while (k < iEnd) {
            if (i < iMiddle && (j >= iEnd || self.sortFunc(a[i], a[j], 0) < 0)) {
                b[k] = a[i];
                i = i + 1;
            } else {
                b[k] = a[j];
                j = j + 1;
            }
            k += 1;
        }
    }

    function sortFunc(Type x, Type y, int fieldIndex) returns @tainted int {
        map<anydata> xMapValue = <map<anydata>>x;
        map<anydata> yMapValue = <map<anydata>>y;

        var xFieldValue = xMapValue.get(self.sortFields[fieldIndex]);
        var yFieldValue = yMapValue.get(self.sortFields[fieldIndex]);

        if (xFieldValue is ()) {
            if (yFieldValue is ()) {
                return 0;
            } else {
                return 1;
            }
        } else if (yFieldValue is ()) {
            return -1;
        } else if (xFieldValue is (int|float|decimal)) {
            if (yFieldValue is (int|float|decimal)) {
                int c;
                if (self.sortTypes[fieldIndex]) {
                    c = self.numberSort(xFieldValue, yFieldValue);
                } else {
                    c = self.numberSort(yFieldValue, xFieldValue);
                }
                return self.callNextSortFunc(x, y, c, fieldIndex + 1);
            } else {
                panic error("Inconsistent order field value",
                message = self.sortFields[fieldIndex] + " order field contain non-numeric values");
            }
        } else if (xFieldValue is string) {
            if (yFieldValue is string) {
                int c;
                if (self.sortTypes[fieldIndex]) {
                    c = self.stringSort(xFieldValue, yFieldValue);
                } else {
                    c = self.stringSort(yFieldValue, xFieldValue);
                }
                return self.callNextSortFunc(x, y, c, fieldIndex + 1);
            } else {
                panic error("Inconsistent order field value",
                message = self.sortFields[fieldIndex] + " order field contain non-string type values");
            }
        } else if (xFieldValue is boolean) {
            if (yFieldValue is boolean) {
                int c;
                if (self.sortTypes[fieldIndex]) {
                    c = self.booleanSort(xFieldValue, yFieldValue);
                } else {
                    c = self.booleanSort(yFieldValue, xFieldValue);
                }
                return self.callNextSortFunc(x, y, c, fieldIndex + 1);
            } else {
                  panic error("Inconsistent order field value",
                  message = self.sortFields[fieldIndex] + " order field contain non-boolean type values");
            }
        } else {
            panic error("Unable to perform order by",
            message = self.sortFields[fieldIndex] + " field type incorrect");
        }
    }

    public function numberSort(int|float|decimal val1, int|float|decimal val2) returns int {
        if (val1 is int) {
            if (val2 is int) {
                return val1 - val2;
            } else if (val2 is float) {
                return <float>val1 < val2 ? -1 : <float>val1 == val2 ? 0 : 1;
            } else {
                return <decimal>val1 < val2 ? -1 : <decimal>val1 == val2 ? 0 : 1;
            }
        } else if (val1 is float) {
            if (val2 is int) {
                return val1 < <float>val2 ? -1 : val1 == <float>val2 ? 0 : 1;
            } else if (val2 is float){
                return val1 < val2 ? -1 : val1 == val2 ? 0 : 1;
            } else {
                return <decimal>val1 < val2 ? -1 : <decimal>val1 == val2 ? 0 : 1;
            }
        } else {
            if (val2 is (int|float)) {
                return val1 < <decimal>val2 ? -1 : val1 == <decimal>val2 ? 0 : 1;
            } else {
                return val1 < val2 ? -1 : val1 == val2 ? 0 : 1;
            }
        }
    }

    public function stringSort(string st1, string st2) returns int {
        return strings:codePointCompare(st1, st2);
    }

    public function booleanSort(boolean b1, boolean b2) returns int {
        if (b1) {
            if (b2) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (b2) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    function callNextSortFunc(Type x, Type y, int c, int fieldIndex) returns @tainted int {
        int result = c;
        if (result == 0 && (self.sortTypes.length() > fieldIndex)) {
            result = self.sortFunc(x, y, fieldIndex);
        }
        return result;
    }

};

// TODO: This for debugging purposes, remove once completed.
public function print(any|error? data) = external;
