import {refreshLogin} from "../../app/OAuth2Helper";
import fetchMock from "jest-fetch-mock";

describe("refreshLogin", ()=>{
    beforeEach(()=>{
        localStorage.clear();
        fetchMock.resetMocks();
    })

    it("should call out to the IdP endpoint with the refresh token and resolve on success", async ()=>{
        fetchMock.mockResponseOnce(JSON.stringify({
            access_token: "some-access-token",
            refresh_token: "some-new-refresh-token"
        }));

        localStorage.setItem("vaultdoor:refresh-token","some-old-refresh-token");
        await refreshLogin("https://fake-token-uri/endpoint");

        expect(localStorage.getItem("vaultdoor:access-token")).toEqual("some-access-token");
        expect(localStorage.getItem("vaultdoor:refresh-token")).toEqual("some-new-refresh-token");
    });

    it("should reject with Request forbidden if the server returns a 403", async ()=>{
        fetchMock.mockReturnValue(Promise.resolve(new Response(JSON.stringify({"status":"forbidden"}),{status:403})));

        localStorage.setItem("vaultdoor:refresh-token","some-old-refresh-token");
        try {
            await refreshLogin("https://fake-token-uri/endpoint");
            throw "Promise did not reject as expected"
        } catch(err) {
            if(err==="Request forbidden") {
                return;
            } else {
                throw err;
            }
        }
    });

    it("should reject if the server returns an unexpected code", async ()=>{
        fetchMock.mockReturnValue(Promise.resolve(new Response(JSON.stringify({"status":"coffee"}),{status:418})));

        localStorage.setItem("vaultdoor:refresh-token","some-old-refresh-token");
        try {
            await refreshLogin("https://fake-token-uri/endpoint");
            throw "Promise did not reject as expected"
        } catch(err) {
            if(err==="Unexpected response") {
                return;
            } else {
                throw err;
            }
        }
    });

    it("should retry on a 503 or 500 error", async ()=>{
        fetchMock.mockResponses([
            "",
            {status: 503}
        ],[
            JSON.stringify({"status":"broken"}),
            {status: 500}
        ],[
            JSON.stringify({
                access_token: "some-access-token",
                refresh_token: "some-new-refresh-token"
            }),
            {status: 200}
        ]);

        localStorage.setItem("vaultdoor:refresh-token","some-old-refresh-token");
        await refreshLogin("https://fake-token-uri/endpoint");
        expect(localStorage.getItem("vaultdoor:access-token")).toEqual("some-access-token");
        expect(localStorage.getItem("vaultdoor:refresh-token")).toEqual("some-new-refresh-token");
    })
})