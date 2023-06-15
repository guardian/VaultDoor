import React from "react";
import PropTypes from "prop-types";
import LoadingIndicator from "./LoadingIndicator.jsx";

class LoginButton extends React.Component {
  static propTypes = {
    oAuthUri: PropTypes.string.isRequired,
    tokenUri: PropTypes.string.isRequired,
    clientId: PropTypes.string.isRequired,
    redirectUri: PropTypes.string.isRequired,
    scope: PropTypes.string.isRequired,
  };

  generateCodeChallenge() {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    const str = array.reduce((acc, x) => acc + x.toString(16).padStart(2, '0'), "");
    sessionStorage.setItem("cx", str);
    return str;
  }

  makeLoginURL() {
    const args = {
      response_type: "code",
      client_id: this.props.clientId,
      scope: this.props.scope,
      redirect_uri: this.props.redirectUri,
      state: "/",
    };

    const encoded = Object.entries(args).map(
      ([k, v]) => `${k}=${encodeURIComponent(v)}`
    );

    return this.props.oAuthUri + "?" + encoded.join("&") + "&code_challenge=" + this.generateCodeChallenge();
  }

  constructor(props) {
    super(props);
    this.startLogin = this.startLogin.bind(this);
  }

  /**
   * send the user out to the IdP to start the login process
   */
  startLogin() {
    window.location = this.makeLoginURL();
  }

  render() {
    if (
      !this.props.oAuthUri ||
      !this.props.tokenUri ||
      !this.props.clientId ||
      !this.props.redirectUri
    ) {
      console.error("Missing some openauth parameters: ", this.props);
      return <LoadingIndicator blank={true} />;
    } else {
      return (
        <button onClick={this.startLogin}>
          &gt;&nbsp;&nbsp;Log in&nbsp;&nbsp;&lt;
        </button>
      );
    }
  }
}

export default LoginButton;
