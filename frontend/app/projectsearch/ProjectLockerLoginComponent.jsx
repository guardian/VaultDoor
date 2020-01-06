import React from 'react';
import PropTypes from "prop-types";

class ProjectLockerLoginComponent extends React.Component {
    static propTypes = {
        projectLockerBaseUrl: PropTypes.string.isRequired,
        loginSuccess: PropTypes.func.isRequired,
        loginFailure: PropTypes.func
    };

    constructor(props) {
        super(props);

        this.state = {
            enteredUid: "",
            enteredPw: "",
            lastError: null,
            loading: false
        };

        this.performLogin = this.performLogin.bind(this);
    }

    async performLogin() {
        const loginData = {
            username: this.state.enteredUid,
            password: this.state.enteredPw
        };

        const result = await fetch(this.props.projectLockerBaseUrl + "/api/login",{method:"POST", body: JSON.stringify(loginData), headers:{"Content-Type": "application/json"}, credentials: "include"});
        const body = await result.text();
        if(result.ok){
            this.setState({loading: false, lastError: null}, ()=>this.props.loginSuccess());
        } else if(result.status===403) {
            this.setState({loading: false, lastError: "Permission denied, please check your username and password"}, ()=>this.props.loginFailure ? this.props.loginFailure() : noop());
        } else {
            this.setState({loading: false, lastError: body});
        }
    };

    render() {
        return <div className="sub-login-box">
            <label htmlFor="id-pl-username">User name</label>
            <input type="text" onChange={evt=>this.setState({enteredUid: evt.target.value})} value={this.state.enteredUid} id="id-pl-username" disabled={this.state.loading}/>
            <label htmlFor="id-pl-passwd">Password</label>
            <input type="password" onChange={evt=>this.setState({enteredPw: evt.target.value})} value={this.state.enteredPw} id="id-pl-passwd" disabled={this.state.loading}/>
            <button onClick={this.performLogin}>Log in</button>
            <p className="error" style={{display: this.state.lastError ? "block": "hidden"}}>{this.state.lastError}</p>
        </div>
    }
}

export default ProjectLockerLoginComponent;