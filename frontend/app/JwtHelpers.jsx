import * as jose from 'jose';

async function checkToken(token, keyURL) {
  const JWKS = jose.createRemoteJWKSet(new URL(keyURL));

  const { payload, protectedHeader } = await jose
      .jwtVerify(token, JWKS)
      .catch(async (error) => {
        if (error?.code === 'ERR_JWKS_MULTIPLE_MATCHING_KEYS') {
          for await (const publicKey of error) {
            console.log("publicKey: " + publicKey);
            try {
              return await jose.jwtVerify(token, publicKey, options)
            } catch (innerError) {
              if (innerError?.code === 'ERR_JWS_SIGNATURE_VERIFICATION_FAILED') {
                continue
              }
              throw innerError
            }
          }
          throw new jose.errors.JWSSignatureVerificationFailed()
        }
        throw error
      })
}

/**
 * Perform the validation of the token via jose library.
 * If validation fails then the returned promise is rejected.
 * If validation succeeds, then the promise only completes once the decoded content has been set into the state.
 * @returns {Promise<object>} Decoded JWT content or rejects with an error
 */
function validateAndDecode(token, signingKey, keyURL, refreshToken) {
  return new Promise((resolve, reject) => {
    try {
      const {payload, protectedHeader} = checkToken(token, keyURL);
      window.localStorage.setItem("vaultdoor:access-token", token); //It validates so, save the token
      if (refreshToken)
        window.localStorage.setItem("vaultdoor:refresh-token", refreshToken);
      resolve(payload);
    } catch (err) {
      console.log("Token: ", token);
      console.error("Could not verify JWT: ", err);
      reject(err)
    }
  });
}

/**
 * gets the signing key from the server
 * @returns {Promise<string>} Raw content of the signing key in PEM format
 */
async function loadInSigningKey() {
  const result = await fetch("/meta/oauth/publickey.pem");
  switch (result.status) {
    case 200:
      return result.text();
    default:
      console.error(
        "could not retrieve signing key, server gave us ",
        result.status
      );
      throw "Could not retrieve signing key";
  }
}

/**
 * returns the raw JWT for passing to backend services
 * @returns {string} the JWT, or null if it is not set.
 */
function getRawToken() {
  return window.localStorage.getItem("vaultdoor:access-token");
}

export { validateAndDecode, loadInSigningKey, getRawToken };
