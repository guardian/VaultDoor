import React from "react";
import { render } from "react-dom";
import {
  BrowserRouter,
  Link,
  Route,
  Switch,
  Redirect,
  withRouter,
} from "react-router-dom";
import RootComponent from "./RootComponent.jsx";
import Raven from "raven-js";
import SearchComponent from "./SearchComponent.jsx";
import OAuthCallbackComponent from "./OAuthCallbackComponent.jsx";
import ByProjectComponent from "./ByProjectComponent.jsx";
import LoadingIndicator from "./LoadingIndicator.jsx";
import { authenticatedFetch } from "./auth";
import { ThemeProvider } from "@material-ui/core/styles";
import { createMuiTheme } from "@material-ui/core";

class App extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      isLoggedIn: false,
      currentUsername: "",
      isAdmin: false,
      loading: true,
      loginDetail: null,
      redirectingTo: null,
      clientId: "",
      resource: "",
      oAuthUri: "",
      tokenUri: "",
      startup: true,
    };

    this.returnToRoot = this.returnToRoot.bind(this);

    this.theme = createMuiTheme({
      palette: {
        type: "dark",
        primary: {
          main: "#f5f5f5", //same as "whitesmoke"
        },
      },
      typography: {
        fontFamily: `'DejaVu Sans Mono', monospace`,
        fontSize: 14,
      },
      overrides: {
        MuiMenuItem: {
          root: {
            backgroundColor: "#000000",
            color: "#f5f5f5",
          },
          selected: {
            color: "#000000",
            backgroundColor: "#f5f5f5ff",
          },
        },
        MuiListItem: {
          root: {
            backgroundColor: "#000000",
            color: "#f5f5f5",
          },
          selected: {
            color: "#000000",
            backgroundColor: "#f5f5f5ff",
          },
          "selected:hover": {
            color: "#000000",
            backgroundColor: "#f5f5f5ff",
          },
        },
      },
    });

    const currentUri = new URL(window.location.href);
    this.redirectUri =
      currentUri.protocol + "//" + currentUri.host + "/oauth2/callback";

    authenticatedFetch("/system/publicdsn").then(async (response) => {
      if (response.status === 200) {
        try {
          const responseJson = await response.json();
          Raven.config(responseJson.publicDsn).install();
          console.log("Sentry initialised for " + responseJson.publicDsn);
        } catch (error) {
          console.error("Could not intialise sentry", error);
        }
      } else {
        const responseBody = await response.text();
        console.error("Could not get public DSN from backend: ", responseBody);
      }
    });
  }

  returnToRoot() {
    this.props.history.push("/");
  }

  setStatePromise(newstate) {
    return new Promise((resolve, reject) =>
      this.setState(newstate, () => resolve())
    );
  }

  checkLogin() {
    return new Promise((resolve, reject) =>
      this.setState({ loading: true, haveChecked: true }, async () => {
        const response = await authenticatedFetch("/api/isLoggedIn");
        if (response.status === 200) {
          const responseJson = await response.json();
          this.setState(
            {
              isLoggedIn: true,
              loading: false,
              currentUsername: responseJson.uid,
              isAdmin: responseJson.isAdmin,
            },
            () => resolve()
          );
        } else if (response.status === 403 || response.status === 401) {
          try {
            const responseJson = await response.json();

            this.setState({
              isLoggedIn: false,
              loading: false,
              loginDetail: responseJson.detail,
            });
          } catch (e) {
            const responseText = await response.text();
            console.error(
              "Permission denied but response invalid: ",
              responseText
            );
            this.setState({
              isLoggedIn: false,
              loading: false,
              loginDetail: "Permission denied, but response was invalid",
            });
          }
        } else {
          const serverError = await response.text();
          this.setState(
            {
              isLoggedIn: false,
              loginDetail: serverError,
              loading: false,
              currentUsername: "",
            },
            () => resolve()
          );
        }
      })
    );
  }

  async loadOauthData() {
    const response = await fetch("/meta/oauth/config.json");
    switch (response.status) {
      case 200:
        console.log("got response data");
        try {
          const content = await response.json();

          return this.setStatePromise({
            clientId: content.clientId,
            resource: content.resource,
            oAuthUri: content.oAuthUri,
            tokenUri: content.tokenUri,
            startup: false,
          });
        } catch (err) {
          console.error("Could not load oauth config: ", err);
          return this.setStatePromise({
            loginDetail:
              "Could not load auth configuration, please contact multimediatech",
            startup: false,
          });
        }
      case 404:
        await response.text(); //consume body and discard it
        return this.setStatePromise({
          startup: false,
          lastError:
            "Metadata not found on server, please contact administrator",
        });
      default:
        await response.text(); //consume body and discard it
        return this.setStatePromise({
          startup: false,
          lastError:
            "Server returned a " +
            response.status +
            " error trying to access metadata",
        });
    }
  }

  async componentDidMount() {
    await this.loadOauthData();
    await this.checkLogin();

    if (!this.state.loading && !this.state.isLoggedIn) {
      this.setState({ redirectingTo: "/" });
    }
  }

  render() {
    return (
      <ThemeProvider theme={this.theme}>
        {this.state.loading || this.state.startup ? (
          <LoadingIndicator />
        ) : (
          <div>
            <h1
              style={{ marginTop: 0 }}
              onClick={this.returnToRoot}
              className="clickable"
            >
              VaultDoor
            </h1>
            <Switch>
              <Route path="/byproject" component={ByProjectComponent} />
              <Route path="/search" component={SearchComponent} />
              <Route
                exact
                path="/oauth2/callback"
                render={(props) => (
                  <OAuthCallbackComponent
                    {...props}
                    oAuthUri={this.state.oAuthUri}
                    tokenUri={this.state.tokenUri}
                    clientId={this.state.clientId}
                    redirectUri={this.redirectUri}
                    resource={this.state.resource}
                  />
                )}
              />
              <Route
                exact
                path="/"
                component={() => (
                  <RootComponent
                    currentUsername={this.state.currentUsername}
                    isLoggedIn={this.state.isLoggedIn}
                    loginErrorDetail={this.state.loginDetail}
                    isAdmin={this.state.isAdmin}
                    oAuthUri={this.state.oAuthUri}
                    tokenUri={this.state.tokenUri}
                    clientId={this.state.clientId}
                    resource={this.state.resource}
                  />
                )}
              />
            </Switch>
          </div>
        )}
      </ThemeProvider>
    );
  }
}

const AppWithRouter = withRouter(App);

render(
  <BrowserRouter root="/">
    <AppWithRouter />
  </BrowserRouter>,
  document.getElementById("app")
);
