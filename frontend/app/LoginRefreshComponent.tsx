import React, { useState, useEffect, useRef } from "react";
import { JwtDataShape } from "./DecodedProfile";
import { refreshLogin } from "./OAuth2Helper";

interface LoginComponentProps {
  refreshToken?: string;
  checkInterval?: number;
  loginData: JwtDataShape;
  onLoginRefreshed?: () => void;
  onLoginCantRefresh?: (reason: string) => void;
  onLoginExpired: () => void;
  onLoggedOut?: () => void;
  overrideRefreshLogin?: (tokenUri: string) => Promise<void>; //only used for testing
  tokenUri: string;
}

const LoginRefreshComponent: React.FC<LoginComponentProps> = (props) => {
  const [refreshFailed, setRefreshFailed] = useState<boolean>(false);

  let loginDataRef = useRef(props.loginData);
  const tokenUriRef = useRef(props.tokenUri);
  const overrideRefreshLoginRef = useRef(props.overrideRefreshLogin);

  useEffect(() => {
    const intervalTimerId = window.setInterval(
      checkExpiryHandler,
      props.checkInterval ?? 60000
    );

    return () => {
      console.log("Removing checkExpiryHandler");
      window.clearInterval(intervalTimerId);
    };
  }, []);

  useEffect(() => {
    console.log("refreshFailed was toggled to ", refreshFailed);
    if (refreshFailed) {
      console.log("Setting countdown handler");
      const intervalTimerId = window.setInterval(updateCountdownHandler, 1000);
      return () => {
        console.log("Cleared countdown handler");
        window.clearInterval(intervalTimerId);
      };
    }
  }, [refreshFailed]);

  useEffect(() => {
    loginDataRef.current = props.loginData;
  }, [props.loginData]);

  /**
   * Called periodically every second once a refresh has failed
   */
  const updateCountdownHandler = () => {
    const nowTime = new Date().getTime() / 1000; //assume time is in seconds
    const expiry = loginDataRef.current.exp;
    const timeToGo = expiry - nowTime;

    if (timeToGo <= 1 && props.onLoginExpired) props.onLoginExpired();
  };

  /**
   * Lightweight function that is called every minute to verify the state of the token
   * it returns a promise that resolves when the component state has been updated. In normal usage this
   * is ignored but it is used in testing to ensure that the component state is only checked after it has been set.
   */
  const checkExpiryHandler = () => {
    if (loginDataRef.current) {
      const nowTime = new Date().getTime() / 1000; //assume time is in seconds
      //we know that it is not null due to above check
      const expiry = loginDataRef.current.exp;
      const timeToGo = expiry - nowTime;

      if (timeToGo <= 120) {
        console.log("Less than 2mins to expiry, attempting refresh...");

        let refreshedPromise;

        if (overrideRefreshLoginRef.current) {
          refreshedPromise = overrideRefreshLoginRef.current(
            tokenUriRef.current
          );
        } else {
          refreshedPromise = refreshLogin(tokenUriRef.current);
        }

        refreshedPromise
          .then(() => {
            console.log("Login refreshed");
            setRefreshFailed(false);

            if (props.onLoginRefreshed) props.onLoginRefreshed();
          })
          .catch((errString) => {
            if (props.onLoginCantRefresh) props.onLoginCantRefresh(errString);
            setRefreshFailed(true);
            updateCountdownHandler();
            return;
          });
      }
    } else {
      console.log("No login data present for expiry check");
    }
  };

  return <></>;
};

export default LoginRefreshComponent;
