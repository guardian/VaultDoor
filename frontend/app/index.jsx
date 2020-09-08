import React from 'react';
import {render} from 'react-dom';
import {BrowserRouter, Link, Route, Switch, Redirect, withRouter} from 'react-router-dom';
import RootComponent from './RootComponent.jsx';
import axios from 'axios';
import Raven from 'raven-js';
import SearchComponent from './SearchComponent.jsx';
import { library } from '@fortawesome/fontawesome-svg-core'

import { faFolder, faFolderOpen, faTimes, faSearch, faCog } from '@fortawesome/free-solid-svg-icons'
import ByProjectComponent from "./ByProjectComponent.jsx";
import LoginComponent from "./LoginComponent.jsx";
import LoadingIndicator from "./LoadingIndicator.jsx";

library.add(faFolderOpen, faFolder, faTimes, faSearch, faCog);

class App extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            isLoggedIn: false,
            currentUsername: "",
            isAdmin: false,
            loading: true,
            redirectingTo: null
        };

        this.onLoggedIn = this.onLoggedIn.bind(this);
        this.onLoggedOut = this.onLoggedOut.bind(this);

        this.returnToRoot = this.returnToRoot.bind(this);

        axios.get("/system/publicdsn").then(response=> {
            Raven
                .config(response.data.publicDsn)
                .install();
            console.log("Sentry initialised for " + response.data.publicDsn);
        }).catch(error => {
            console.error("Could not intialise sentry", error);
        });
    }

    returnToRoot(){
        this.props.history.push("/");
    }

    checkLogin(){
        return new Promise((resolve,reject)=>
            this.setState({loading: true, haveChecked: true}, ()=>
                axios.get("/api/isLoggedIn")
                    .then(response=>{ //200 response means we are logged in
                        this.setState({
                            isLoggedIn: true,
                            loading: false,
                            currentUsername: response.data.uid,
                            isAdmin: response.data.isAdmin
                        }, ()=>resolve());
                    })
                    .catch(error=>{
                        this.setState({
                            isLoggedIn: false,
                            loading: false,
                            currentUsername: ""
                        }, ()=>resolve())
                    })
            )
        );
    }

    componentDidMount(){
        this.checkLogin().then(()=>{
            if(!this.state.loading && !this.state.isLoggedIn) {
                this.setState({redirectingTo: "/" });
            }
        })
    }

    onLoggedIn(userid, isAdmin){
        this.setState({currentUsername: userid, isAdmin: isAdmin, isLoggedIn: true}, ()=>{
            if(this.state.redirectingTo){
                window.location.href = this.state.redirectingTo;
            } else {
                if (!isAdmin) window.location.href = "/project/?mine";
            }
        })
    }

    onLoggedOut(){
        this.setState({currentUsername: "", isLoggedIn: false})
    }

    render(){
        if(this.state.loading) {
            return <LoadingIndicator/>;
        }

        if(!this.state.isLoggedIn) {
            return <div>
                <LoginComponent onLoggedIn={this.onLoggedIn} onLoggedOut={this.onLoggedOut} currentlyLoggedIn={this.state.isLoggedIn}/>
            </div>
        }

        return <div>
            <h1 onClick={this.returnToRoot} className="clickable">VaultDoor</h1>
            <Switch>
                <Route path="/byproject" component={ByProjectComponent}/>
                <Route path="/search" component={SearchComponent}/>
                <Route exact path="/" component={()=><RootComponent
                    onLoggedOut={this.onLoggedOut}
                    onLoggedIn={this.onLoggedIn}
                    currentUsername={this.state.currentUsername}
                    isLoggedIn={this.state.isLoggedIn}
                    isAdmin={this.state.isAdmin}
                />}/>
            </Switch>
        </div>
    }
}

const AppWithRouter = withRouter(App);

render(<BrowserRouter root="/"><AppWithRouter/></BrowserRouter>, document.getElementById('app'));