import React from "react";
import MetadataTabView from "../../app/metadata/MetadataTabView.jsx";
import {shallow,mount} from "enzyme";

describe("MetadataTabView.breakdownMetadataString", ()=>{
    it("should split a comma separated list of key=value into an object", ()=>{
        const totest = new MetadataTabView({metaDataString: "key1=value1,key2=value2,  key3=value3"});

        const result = totest.breakdownMetadataString();

        expect(result).toEqual({
            key1: "value1",
            key2: "value2",
            key3: "value3"
        })
    })
});

describe("MetadataTabView", ()=>{
    it("should render a table for only the given keys", ()=>{
        const tabNames = ["tab1","tab2"];
        const prefixes = ["t1","t2"];
        const mdString="t1keything=value,t2keything=value2,t3keything=value3,t2keyother=value4";

        const rendered = shallow(<MetadataTabView tabNames={tabNames} tabPrefixes={prefixes} metaDataString={mdString}/>)
        rendered.setState({selectedTabIndex: 1});
        rendered.update();

        const rows = rendered.find("tr");
        expect(rows.length).toEqual(2);
    });


});