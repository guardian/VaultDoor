import React from "react";
import LoginRefreshComponent from "../../app/LoginRefreshComponent";
import { mount } from "enzyme";
import { JwtDataShape } from "../../app//DecodedProfile";
import sinon from "sinon";
jest.mock("../../app//OAuth2Helper");
import { act } from "react-dom/test-utils";

describe("LoginRefreshComponent", () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });
  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it("should set up an interval on load to check login state", () => {
    const loginExpiredCb = sinon.spy();

    const mockLoginData: JwtDataShape = {
      aud: "my-audience",
      iss: "my-idP",
      iat: 123456,
      exp: 78910,
    };

    const rendered = mount(
      <LoginRefreshComponent
        loginData={mockLoginData}
        onLoginExpired={loginExpiredCb}
        tokenUri="https://fake-token-uri"
      />
    );
    expect(setInterval).toHaveBeenCalledTimes(1);
    expect(loginExpiredCb.callCount).toEqual(0);
  });

  it("should fire a callback if the login was successfully refreshed", async () => {
    const loginExpiredCb = sinon.spy();
    const loginRefreshedCb = sinon.spy();
    const loggedOutCb = sinon.spy();
    const loginCantRefreshCb = sinon.spy();

    const mockRefresh = sinon.stub();
    mockRefresh.returns(Promise.resolve());

    const mockLoginData: JwtDataShape = {
      aud: "my-audience",
      iss: "my-idP",
      iat: new Date().getTime() / 1000,
      exp: 78910,
    };

    const rendered = mount(
      <LoginRefreshComponent
        loginData={mockLoginData}
        onLoginExpired={loginExpiredCb}
        onLoginRefreshed={loginRefreshedCb}
        onLoggedOut={loggedOutCb}
        onLoginCantRefresh={loginCantRefreshCb}
        overrideRefreshLogin={mockRefresh}
        tokenUri="https://fake-token-uri"
      />
    );

    act(() => {
      jest.advanceTimersByTime(60001);
    });
    await act(() => Promise.resolve()); //this allows other outstanding promises to resolve _first_, including the one that
    //sets the component state and calls loginRefreshedCb
    expect(loginRefreshedCb.calledOnce).toBeTruthy();
    expect(loginExpiredCb.callCount).toEqual(0);
    expect(loginCantRefreshCb.callCount).toEqual(0);
    expect(loggedOutCb.callCount).toEqual(0);
  });

  it("should fire a callback if the refresh failed", async () => {
    const loginExpiredCb = sinon.spy();
    const loginRefreshedCb = sinon.spy();
    const loggedOutCb = sinon.spy();
    const loginCantRefreshCb = sinon.spy();

    const mockRefresh = sinon.stub();
    mockRefresh.returns(Promise.reject("Something went bang"));

    const mockLoginData: JwtDataShape = {
      aud: "my-audience",
      iss: "my-idP",
      iat: new Date().getTime() / 1000,
      exp: new Date().getTime() / 1000 + 30,
    };

    const rendered = mount(
      <LoginRefreshComponent
        loginData={mockLoginData}
        onLoginExpired={loginExpiredCb}
        onLoginRefreshed={loginRefreshedCb}
        onLoggedOut={loggedOutCb}
        onLoginCantRefresh={loginCantRefreshCb}
        overrideRefreshLogin={mockRefresh}
        tokenUri="https://fake-token-uri"
      />
    );

    act(() => {
      jest.advanceTimersByTime(60001);
    });

    await act(() => Promise.resolve()); //this allows other outstanding promises to resolve _first_, including the one that
    //sets the component state and calls loginRefreshedCb
    expect(loginRefreshedCb.callCount).toEqual(0);
    expect(loggedOutCb.callCount).toEqual(0);
    expect(loginCantRefreshCb.callCount).toEqual(1);
    expect(loginExpiredCb.callCount).toEqual(0);
  });

  it("should alert the parent when the login actually expires", async () => {
    const loginExpiredCb = sinon.spy();
    const loginRefreshedCb = sinon.spy();
    const loggedOutCb = sinon.spy();
    const loginCantRefreshCb = sinon.spy();

    const mockRefresh = sinon.stub();
    mockRefresh.returns(Promise.reject("Something went bang"));

    const mockLoginData: JwtDataShape = {
      aud: "my-audience",
      iss: "my-idP",
      iat: new Date().getTime() / 1000 - 3600,
      exp: new Date().getTime() / 1000 - 10,
    };

    const rendered = mount(
      <LoginRefreshComponent
        loginData={mockLoginData}
        onLoginExpired={loginExpiredCb}
        onLoginRefreshed={loginRefreshedCb}
        onLoggedOut={loggedOutCb}
        onLoginCantRefresh={loginCantRefreshCb}
        overrideRefreshLogin={mockRefresh}
        tokenUri="https://fake-token-uri"
      />
    );

    act(() => {
      jest.advanceTimersByTime(60001);
    });

    await act(() => Promise.resolve()); //this allows other outstanding promises to resolve _first_, including the one that
    //sets the component state and calls callbacks
    expect(loginRefreshedCb.callCount).toEqual(0);
    expect(loggedOutCb.callCount).toEqual(0);
    expect(loginCantRefreshCb.callCount).toEqual(1);
    expect(loginExpiredCb.callCount).toEqual(1);
  });
});
