import OAuthConfigurationTI from "./OAuthConfiguration-ti";
import { createCheckers } from "ts-interface-checker";

interface OAuthConfigurationIF {
  clientId: string;
  resource: string;
  oAuthUri: string;
  tokenUri: string;
  adminClaimName: string;
  scope: string;
}

const { OAuthConfigurationIF } = createCheckers(OAuthConfigurationTI);

class OAuthConfiguration implements OAuthConfigurationIF {
  clientId: string;
  resource: string;
  oAuthUri: string;
  tokenUri: string;
  adminClaimName: string;
  scope: string;
  tokenSigningCertPath: string;

  constructor(from: any, validate = true) {
    if (validate) {
      //this will throw an error (VError from ts-interface-checker) if the configuration does not validate
      OAuthConfigurationIF.check(from);
    }
    this.clientId = from.clientId;
    this.resource = from.resource;
    this.oAuthUri = from.oAuthUri;
    this.tokenUri = from.tokenUri;
    this.adminClaimName = from.adminClaimName;
    this.scope = from.scope;
    this.tokenSigningCertPath = from.tokenSigningCertPath;
  }

  /**
   * returns a boolean indicating whether the frontend should treat this user as an admin or not
   * @param claimData
   */
  isAdmin(claimData: any) {
    return claimData.hasOwnProperty(this.adminClaimName);
  }
}

export type { OAuthConfigurationIF };
export default OAuthConfiguration;
