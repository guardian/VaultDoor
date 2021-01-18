import React from "react";
import PropTypes from "prop-types";
import { authenticatedFetch } from "../auth";

class ProjectLockerLoginComponent extends React.Component {
  static propTypes = {
    projectLockerBaseUrl: PropTypes.string.isRequired,
    loginSuccess: PropTypes.func.isRequired,
    loginFailure: PropTypes.func,
  };

  constructor(props) {
    super(props);

    this.state = {
      enteredUid: "",
      enteredPw: "",
      lastError: null,
      loading: false,
    };

    this.performLogin = this.performLogin.bind(this);
  }

  noop() {}

  async performLogin() {
    const loginData = {
      username: this.state.enteredUid,
      password: this.state.enteredPw,
    };

    try {
      const result = await authenticatedFetch(
        this.props.projectLockerBaseUrl + "/api/login",
        {
          method: "POST",
          body: JSON.stringify(loginData),
          headers: { "Content-Type": "application/json" },
          credentials: "include",
        }
      );
      const body = await result.text();
      if (result.ok) {
        const content = JSON.parse(body);
        this.setState({ loading: false, lastError: null }, () =>
          this.props.loginSuccess(content.uid)
        );
      } else if (result.status === 403) {
        this.setState(
          {
            loading: false,
            lastError:
              "Permission denied, please check your username and password",
          },
          () =>
            this.props.loginFailure ? this.props.loginFailure() : this.noop()
        );
      } else {
        this.setState({ loading: false, lastError: body });
      }
    } catch (err) {
      if (!err.toString().includes("Not logged in"))
        this.setState({ lastError: err.toString() });
    }
  }

  render() {
    return (
      <div className="sub-login-box centered">
        <label htmlFor="id-pl-username">User name</label>
        <input
          type="text"
          onChange={(evt) => this.setState({ enteredUid: evt.target.value })}
          value={this.state.enteredUid}
          id="id-pl-username"
          disabled={this.state.loading}
        />
        <label htmlFor="id-pl-passwd">Password</label>
        <input
          type="password"
          onChange={(evt) => this.setState({ enteredPw: evt.target.value })}
          value={this.state.enteredPw}
          id="id-pl-passwd"
          disabled={this.state.loading}
        />
        <button onClick={this.performLogin}>Log in</button>
        <p
          className="error"
          style={{ display: this.state.lastError ? "block" : "hidden" }}
        >
          {this.state.lastError}
        </p>
      </div>
    );
  }
}

export default ProjectLockerLoginComponent;
