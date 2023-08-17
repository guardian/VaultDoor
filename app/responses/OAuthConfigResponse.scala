package responses

/**
 * this response is the format for the oauth2 configuration to be sent back to the frontend
 */
case class OAuthConfigResponse(
                              clientId: String,
                              resource: String,
                              oAuthUri: String,
                              tokenUri: String,
                              allowedAudiences: Seq[String],
                              adminClaimName: String,
                              scope: String,
                              tokenSigningCertPath: String
                              )
