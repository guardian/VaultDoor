import React from "react";
import PropTypes from "prop-types";
import LoginButton from "./LoginButton.jsx";
import { Link } from "react-router-dom";

class RootComponent extends React.Component {
  static propTypes = {
    currentUsername: PropTypes.string,
    isLoggedIn: PropTypes.bool.isRequired,
    loginErrorDetail: PropTypes.string,
    oAuthUri: PropTypes.string.isRequired,
    tokenUri: PropTypes.string.isRequired,
    clientId: PropTypes.string.isRequired,
    resource: PropTypes.string.isRequired,
  };

  constructor(props) {
    super(props);

    const currentUri = new URL(window.location.href);
    this.redirectUri =
      currentUri.protocol + "//" + currentUri.host + "/oauth2/callback";
  }

  doLogout() {
    localStorage.clear();
    window.location.href = "/";
  }

  render() {
    if (this.props.isLoggedIn) {
      return (
        <div>
          <p className="inline-dialog-content centered">
            You are currently logged in as
            <i
              className="fa fa-user"
              style={{ marginRight: "3px", marginLeft: "5px" }}
            />
            <span className="emphasis">{this.props.currentUsername}</span>
          </p>

          <ul className="main-menu-list">
            <li className="main-menu-list">
              <Link to="/search">Go to file search</Link>
            </li>
            <li className="main-menu-list">
              <Link to="/byproject">Go to project browser</Link>
            </li>
          </ul>

          <button style={{ marginLeft: "1em" }} onClick={this.doLogout}>
            Log out
          </button>
        </div>
      );
    } else {
      return (
        <div>
          {this.props.loginErrorDetail ? (
            <p className="error">{this.props.loginErrorDetail}</p>
          ) : null}
          Click here to log in to VaultDoor:{" "}
          <LoginButton
            oAuthUri={this.props.oAuthUri}
            tokenUri={this.props.tokenUri}
            clientId={this.props.clientId}
            redirectUri={this.redirectUri}
            resource={this.props.resource}
            state={this.props.redirectingTo}
          />
        </div>
      );
    }
  }
}

export default RootComponent;
