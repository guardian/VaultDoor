import React from "react";
import PropTypes from "prop-types";
import LoadingIndicator from "./LoadingIndicator.jsx";

class LoginButton extends React.Component {
    static propTypes = {
        oAuthUri: PropTypes.string.isRequired,
        tokenUri: PropTypes.string.isRequired,
        clientId: PropTypes.string.isRequired,
        redirectUri: PropTypes.string.isRequired,
        resource: PropTypes.string.isRequired,
    };

    makeLoginURL() {
        const args = {
            response_type: "code",
            client_id: this.props.clientId,
            resource: this.props.resource,
            redirect_uri: this.props.redirectUri,
            state: "/",
        };

        const encoded = Object.entries(args).map(
            ([k, v]) => `${k}=${encodeURIComponent(v)}`
        );

        return this.props.oAuthUri + "?" + encoded.join("&");
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
        if(!this.props.oAuthUri || !this.props.tokenUri || !this.props.clientId || !this.props.redirectUri || !this.props.resource) {
            console.error("Missing some openauth parameters: ", this.props);
            return <LoadingIndicator blank={true}/>
        } else {
            return <button onClick={this.startLogin}>&gt;&nbsp;&nbsp;Log in&nbsp;&nbsp;&lt;</button>
        }
    }
}

export default LoginButton;