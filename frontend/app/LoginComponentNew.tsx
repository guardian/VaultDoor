import React, { useState, useEffect } from "react";
import { loadInSigningKey, validateAndDecode } from "./JwtHelpers";
import { JwtData, JwtDataShape } from "./DecodedProfile";
import OAuthConfiguration from "./OAuthConfiguration";
import { VError } from "ts-interface-checker";
import LoginRefreshComponent from "./LoginRefreshComponent";

interface LoginButtonNewProps {
  onLoggedIn?: () => void;
  onLoggedOut?: () => void;
  onLoginValid?: (valid: boolean, jwtDataShape?: JwtDataShape) => void;
}

const LoginComponentNew: React.FC<LoginButtonNewProps> = (props) => {
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);
  const [loginData, setLoginData] = useState<JwtDataShape | null>(null);
  const [expired, setExpired] = useState<boolean>(false);

  // config
  const [tokenUri, setTokenUri] = useState<string>("");

  const loadConfig: () => Promise<OAuthConfiguration> = async () => {
    const response = await fetch("/meta/oauth/config.json");
    if (response.status === 200) {
      const data = await response.json();
      const config = new OAuthConfiguration(data); //validates the configuration and throws a VError if it fails
      setTokenUri(config.tokenUri);
      return config;
    } else {
      throw `Server returned ${response.status}`;
    }
  };

  const validateToken: (config: OAuthConfiguration) => Promise<void> = async (
    config: OAuthConfiguration
  ) => {
    const token = window.localStorage.getItem("vaultdoor:access-token");
    if (!token) return;

    try {
      const signingKey = await loadInSigningKey();

      const decodedData = await validateAndDecode(token, signingKey, config.tokenSigningCertPath);
      const loginData = JwtData(decodedData);
      setLoginData(loginData);

      // Login valid callback if provided
      if (props.onLoginValid) {
        props.onLoginValid(true, loginData);
      }

      setIsLoggedIn(true);
    } catch (error: any) {
      // Login valid callback if provided
      if (props.onLoginValid) {
        props.onLoginValid(false);
      }

      setIsLoggedIn(false);

      if (error.name === "TokenExpiredError") {
        console.error("Token has already expired");
        setExpired(true);
      } else {
        console.error("Existing login token was not valid: ", error);
      }
    }
  };

  /**
   * Load in the oauth config and validate the loaded in token
   */
  const refresh = async () => {
    try {
      const config = await loadConfig();
      await validateToken(config);
    } catch (err) {
      if (err instanceof VError) {
        console.log("OAuth configuration was not valid: ", err);
      } else {
        console.log("Could not load oauth configuration: ", err);
      }
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  return (
    <>
      {isLoggedIn && loginData ? (
        <LoginRefreshComponent
          loginData={loginData}
          onLoggedOut={props.onLoggedOut}
          onLoginRefreshed={() => {
            refresh();
          }}
          onLoginExpired={() => {
            setExpired(true);
            setIsLoggedIn(false);
          }}
          tokenUri={tokenUri}
        />
      ) : null}
    </>
  );
};

export default LoginComponentNew;
