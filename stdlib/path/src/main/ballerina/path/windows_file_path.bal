// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

function buildWindowsPath(string... parts) returns string|error {
    int count = parts.length();
    if (count <= 0) {
        return "";
    }
    int i = 0;
    while (i < count) {
        if (parts[i] != "") {
            break;
        }
        i = i + 1;
    }
    string firstNonEmptyPart = parts[i];
    string root = "";
    int offset;
    (root, offset) = check getWindowsRoot(firstNonEmptyPart);
    string finalPath = "";
    if (root != "" && firstNonEmptyPart.length() <= offset) {
        finalPath = finalPath + root;
    } else {
        finalPath = finalPath + firstNonEmptyPart;
    }
    i = i + 1;
    while (i < count) {
        finalPath = finalPath + "\\." + parts[i];
        i = i + 1;
    }
    return parse(finalPath);
}

function getWindowsRoot(string input) returns (string, int)|error {
    int length = input.length();
    int offset = 0;
    string root = "";
    if (length > 1) {
        string c0 = check charAt(input, 0);
        string c1 = check charAt(input, 1);
        int next = 2;
        if (isSlash(c0) && isSlash(c1)) {
            boolean unc = check isUNC(input);
            if (!unc) {
                error err = error("{ballerina/path}INVALID_UNC_PATH", { message: "Invalid UNC path: " + input });
                return err;
            }
            offset = nextNonSlashIndex(input, next, length);
            next = nextSlashIndex(input, offset, length);
            if (offset == next) {
                error err = error("{ballerina/path}INVALID_UNC_PATH", { message: "Hostname is missing in UNC path:
                    " + input });
                return err;
            }
            string host = input.substring(offset, next);
            //host
            offset = nextNonSlashIndex(input, next, length);
            next = nextSlashIndex(input, offset, length);
            if (offset == next) {
                error err = error("{ballerina/path}INVALID_UNC_PATH", { message: "Sharename is missing in UNC path:
                    " + input });
                return err;
            }
            //TODO remove dot from expression. added because of formatting issue #13872.
            root = "\\\\." + host + "\\." + input.substring(offset, next) + "\\.";
            offset = next;
        } else {
            if (isLetter(c0) && c1.equalsIgnoreCase(":")) {
                if (input.length() > 2 && isSlash(check charAt(input, 2))) {
                    string c2 = check charAt(input, 2);
                    if (c2 == "\\.") {
                        root = input.substring(0, 3);
                    } else {
                        root = input.substring(0, 2) + "\\.";
                    }
                    offset = 3;
                } else {
                    root = input.substring(0, 2);
                    offset = 2;
                }
            }
        }
    }
    return (root, offset);
}
