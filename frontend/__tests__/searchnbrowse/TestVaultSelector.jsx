import React from "react";
import {shallow} from "enzyme";
import sinon from "sinon";
import VaultSelector from "../../app/searchnbrowse/VaultSelector";

describe("VaultSelector", ()=>{
    beforeEach(()=>{
        fetchMock.mockResponse(JSON.stringify([
                    { vaultId: "fake-id-1", name: "Vault 1" },
                    { vaultId: "fake-id-2", name: "Vault 2" }
                ])
        )
    })

    afterEach(()=>{
        fetchMock.resetMocks();
        fetchMock.dontMock();
    });

    it("should render a dropdown of all vaults", (done)=>{
        const vaultChangedCb = sinon.spy();
        const rendered = shallow(<VaultSelector currentvault={""} vaultWasChanged={vaultChangedCb}/>);

        setTimeout(()=>{
            try {
                rendered.update();
                const selector = rendered.find("#vaultsDropdown");
                //console.log(rendered.html());

                expect(selector.children("option").length).toEqual(2);
                expect(selector.children("option").at(0).text()).toEqual("Vault 1");
                expect(selector.children("option").at(0).prop("value")).toEqual("fake-id-1");
                expect(selector.children("option").at(1).text()).toEqual("Vault 2");
                expect(selector.children("option").at(1).prop("value")).toEqual("fake-id-2");
                done();
            } catch(err) {
                done.fail(err);
            }
        }, 1000)

    })
})