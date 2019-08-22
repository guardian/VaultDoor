import React from 'react';
import {render} from 'react-dom';
import {BrowserRouter, Link, Route, Switch, Redirect} from 'react-router-dom';
import Raven from 'raven-js';

class App extends React.Component {
    render(){
        return <div>
            <h1>VaultDoor</h1>
            {/*<h1>Media Census</h1>*/}
            {/*<BannerMenu/>*/}
            {/*<Switch>*/}
            {/*    <Route path="/current" component={CurrentStateStats}/>*/}
            {/*    <Route path="/history" component={StatsHistoryGraph}/>*/}
            {/*    <Route path="/runs" component={RunsAdmin}/>*/}
            {/*    <Route path="/nearlines/membership" component={NearlineStorageMembership}/>*/}
            {/*    <Route path="/nearlines" component={NearlineStorages}/>*/}
            {/*    <Route path="/" component={IndexRedirect}/>*/}
            {/*</Switch>*/}
        </div>
    }
}

render(<BrowserRouter root="/"><App/></BrowserRouter>, document.getElementById('app'));