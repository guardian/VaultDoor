import React from 'react';
import {render} from 'react-dom';
import {BrowserRouter, Link, Route, Switch, Redirect} from 'react-router-dom';
import RootComponent from './RootComponent.jsx';
import axios from 'axios';
import Raven from 'raven-js';
import SearchComponent from './SearchComponent.jsx';
import { library } from '@fortawesome/fontawesome-svg-core'

import { faFolder, faFolderOpen, faTimes, faSearch } from '@fortawesome/free-solid-svg-icons'
import ByProjectComponent from "./ByProjectComponent.jsx";

library.add(faFolderOpen, faFolder, faTimes, faSearch);

class App extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            isLoggedIn: false,
            currentUsername: "",
            isAdmin: false,
            loading: false
        };

        this.onLoggedIn = this.onLoggedIn.bind(this);
        this.onLoggedOut = this.onLoggedOut.bind(this);
        axios.get("/system/publicdsn").then(response=> {
            Raven
                .config(response.data.publicDsn)
                .install();
            console.log("Sentry initialised for " + response.data.publicDsn);
        }).catch(error => {
            console.error("Could not intialise sentry", error);
        });
    }


    checkLogin(){
        this.setState({loading: true, haveChecked: true}, ()=>
            axios.get("/api/isLoggedIn")
                .then(response=>{ //200 response means we are logged in
                    this.setState({
                        isLoggedIn: true,
                        loading: false,
                        currentUsername: response.data.uid,
                        isAdmin: response.data.isAdmin
                    });
                })
                .catch(error=>{
                    this.setState({
                        isLoggedIn: false,
                        loading: false,
                        currentUsername: ""
                    })
                })
        );
    }

    componentWillMount(){
        this.checkLogin();
    }

    onLoggedIn(userid, isAdmin){
        console.log("Logged in as " + userid);
        console.log("Is an admin? " + isAdmin);

        this.setState({currentUsername: userid, isAdmin: isAdmin, isLoggedIn: true}, ()=>{
            if(!isAdmin) window.location.href="/project/?mine";
        })
    }

    onLoggedOut(){
        this.setState({currentUsername: "", isLoggedIn: false})
    }


    render(){
        return <div>
            <h1>VaultDoor</h1>
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

render(<BrowserRouter root="/"><App/></BrowserRouter>, document.getElementById('app'));