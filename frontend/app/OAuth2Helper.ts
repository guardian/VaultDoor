/**
 * Call out to the IdP to request a refresh of the login using the refresh token stored in the local storage.
 * on success, the updated token is stored in the local storage and the promise resolves
 * on failure, the local storage is not touched and the promise rejects with an error string
 * if the server returns a 500 or 503/504 error then it is assumed to be transient and the request will be retried
 * after a two second delay.
 *
 * This is NOT written as a conventional async function in order to utilise more fine-grained control of when the promise
 * is resolved; i.e., it calls itself on a timer in order to retry so we must only resolve the promise once there has been
 * a definitive success or failure of the operation which could be after multiple calls
 * @param tokenUri server uri to make the refresh request to
 * @returns a Promise
 */
export const refreshLogin: (tokenUri: string) => Promise<void> = (tokenUri) =>
  new Promise((resolve, reject) => {
    const refreshToken = localStorage.getItem("vaultdoor:refresh-token");
    if (!refreshToken) {
      reject("No refresh token");
    }

    const postdata: { [index: string]: string } = {
      grant_type: "refresh_token",
      client_id: "vaultdoor",
      refresh_token: refreshToken as string,
    };
    const content_elements = Object.keys(postdata).map(
      (k) => k + "=" + encodeURIComponent(postdata[k])
    );
    const body_content = content_elements.join("&");

    const performRefresh = async () => {
      const response = await fetch(tokenUri, {
        method: "POST",
        body: body_content,
        headers: {
          Accept: "application/json",
          "Content-Type": "application/x-www-form-urlencoded",
        },
      });
      switch (response.status) {
        case 200:
          const content = await response.json();
          console.log("Server response: ", content);
          localStorage.setItem("vaultdoor:access-token", content.access_token);
          if (content.refresh_token)
            localStorage.setItem(
              "vaultdoor:refresh-token",
              content.refresh_token
            );
          resolve();
          break;
        case 403:
        case 401:
          console.log("Refresh was rejected with a forbidden error");
          reject("Request forbidden");
          break;
        case 500:
          console.log("Refresh was rejected due to a server error");
          window.setTimeout(() => performRefresh(), 2000); //try again in two seconds
          break;
        case 503:
        case 504:
          console.log("Authentication server not available");
          window.setTimeout(() => performRefresh(), 2000); //try again in two seconds
          break;
        default:
          const errorbody = await response.text();
          console.log(
            "Unexpected response from authentication server: ",
            response.status,
            errorbody
          );
          reject("Unexpected response");
          break;
      }
    };

    performRefresh().catch((err) => reject(err.toString()));
  });
