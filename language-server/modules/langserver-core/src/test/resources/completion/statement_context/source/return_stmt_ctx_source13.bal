import ballerina/module1;

public listener module1:Listener l1 = new(9090);

service testService on l1 {
    resource function get . () returns int {
        r
    }
}
