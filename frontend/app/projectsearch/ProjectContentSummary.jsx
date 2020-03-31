import React from 'react';
import PropTypes from 'prop-types';
import CommissionProjectView from "../metadata/CommissionProjectView.jsx";
import {Doughnut, Pie} from "react-chartjs-2";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import BytesFormatter from "../common/BytesFormatter.jsx";

class ProjectContentSummary extends React.Component {
    static propTypes = {
        projectId: PropTypes.string,
        vaultId: PropTypes.string.isRequired,
        plutoBaseUrl: PropTypes.string
    };

    constructor(props){
        super(props);
        const emptySummary = {count:0, size:0};
        const pieCasing = {
            datasets: [{
                data: []
            }],
            labels: []
        };

        this.state = {
            lastError: null,
            loading: false,
            summaryData:{
                total: emptySummary
            },
            typePieData: pieCasing,
            fileTypePieData: pieCasing,
            mediaTypePieData: pieCasing,
            showType: true,
            showExtensions: false,
            showMediaType: true
        };

        this.initiateDownload = this.initiateDownload.bind(this);
    }

    setStatePromise(newState){
        return new Promise((resolve, reject)=>this.setState(newState, ()=>resolve()))
    }

    checkVaultId(resolve, reject, iteration){
        if(this.props.vaultId){
            resolve();
        } else {
            const count = iteration ? iteration+1 : 2;
            if(count>10){
                console.error("Timed out waiting for vault ID to be set");
                reject("Timeout");
            }
            window.setTimeout(()=>this.waitForVaultId(resolve, count), 100);
        }
    }

    waitForVaultId() {
        return new Promise((resolve,reject)=>{
            this.checkVaultId(resolve, reject, 0);
        })
    }

    async getSummaryInfo() {
        await this.waitForVaultId();    //when the view is passed a project ID in the URL on a fresh load then this can (on occasion) get triggered _before_ the vaultid is set.
                                        //we chain ourselves to a Promise that only resolves once the vaultid is set, using a promise-timeout that does not block so the backend can
                                        //set up the vaultId property correctly.
        if(!this.props.projectId) return new Promise((resolve,reject)=>reject("no projectid set"));

        const url = "/api/vault/" + this.props.vaultId + "/projectSummary/" + this.props.projectId;
        const result = await fetch(url);
        const bodyText = await result.text();

        if(result.ok){
            const content = JSON.parse(bodyText);
            return this.setStatePromise({loading:false, lastError: null, summaryData: content}).then(()=>this.setupChartData());
        } else {
            console.error(bodyText);
            return this.setStatePromise({loading: false, lastError: bodyText});
        }
    }

    async setupChartData() {
        const updatedTypePieData = {
            datasets: [{
                data: Object.values(this.state.summaryData.gnmType).map(entry=>entry.count)
            }],
            labels: Object.keys(this.state.summaryData.gnmType)
        };

        const updatedFileTypePieData = {
            datasets: [{
                data: Object.values(this.state.summaryData.fileType).map(entry=>entry.count)
            }],
            labels: Object.keys(this.state.summaryData.fileType)
        };

        const updatedMediaTypePieData = {
            datasets: [{
                data: Object.values(this.state.summaryData.mediaType).map(entry=>entry.count)
            }],
            labels: Object.keys(this.state.summaryData.mediaType)
        };

        return this.setStatePromise({typePieData: updatedTypePieData, fileTypePieData: updatedFileTypePieData, mediaTypePieData: updatedMediaTypePieData});
    }

    componentDidMount() {
        this.setState({loading: true}, ()=>
            this.getSummaryInfo().catch(()=>this.setState({loading: false})));
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        if(prevProps.projectId!==this.props.projectId) this.setState({loading: true}, ()=>this.getSummaryInfo());

    }

    async requestDownloadLink() {
        const url = "/api/bulk/new/" + this.props.vaultId + "/" + this.props.projectId;
        const result = await fetch(url);
        const bodyText = await result.text();

        if(result.ok){
            const content = JSON.parse(bodyText);
            if(content.status==="ok" && content.itemClass==="link"){
                return this.setStatePromise({lastError: null}).then(()=>window.location.href = content.entry);
            } else {
                console.log("Got malformed response ", content);
                return this.setStatePromise({lastError: "malformed server response, check javascript logs for details"});
            }
        } else {
            return this.setStatePromise({lastError: bodyText});
        }
    }

    initiateDownload(){
        this.requestDownloadLink().catch(err=>{
            console.error(err);
            this.setState({lastError: "Clientside error, check javascript logs for details"});
        })
    }

    render(){
        if(this.state.loading){
            return <div id="project-content-summary" className="results-panel">
                <p className="centered information">Project {this.props.projectId}<br/>Loading summary information...</p>
                <a href={this.props.plutoBaseUrl + "/project/" + this.props.projectId} target="_blank" style={{fontSize: "1.4em", paddingTop: "0.8em", display: this.props.plutoBaseUrl ? "inherit" : "none"}}>View in Pluto ></a>
                <div className="centered" style={{marginTop: "1em"}}>
                <FontAwesomeIcon icon="cog" className="spinner" size="5x"/>
                </div>
            </div>
        }
        return <div id="project-content-summary" className="results-panel">
            {
                this.props.projectId && this.props.projectId!=="" ? <p className="centered information" style={{marginBottom: "0.6em"}}>Project {this.props.projectId}</p> : <p className="centered information">Select a project above</p>
            }
            {
                this.state.summaryData.total.count===0 && !this.state.loading ? <p className="information centered" style={{fontSize: "1.2em"}}>No media found for this project</p> : <span/>
            }
            <div className="chart-holder">
                <table>
                    <tbody>
                    <tr>
                        <td>Files in this project</td>
                        <td>{this.state.summaryData.total.count}</td>
                    </tr>
                    <tr>
                        <td>Total size of this project</td>
                        <td><BytesFormatter value={this.state.summaryData.total.size}/></td>
                    </tr>
                    </tbody>
                </table>
                <a href={this.props.plutoBaseUrl + "/project/" + this.props.projectId} target="_blank" style={{fontSize: "1.4em", paddingTop: "0.8em", display: this.props.plutoBaseUrl ? "inherit" : "none"}}>View in Pluto ></a>
                <a onClick={this.initiateDownload} style={{fontSize: "1.4em", display: this.state.summaryData.total.count>0 ? "block" : "none"}} className="clickable">Open in Download Manager ></a>
                <p className="error">{this.state.lastError}</p>
            </div>
            <div className="chart-holder" style={{display: this.state.showType ? "inline-block" : "none"}}>
                <Doughnut data={this.state.typePieData}/>
            </div>
            <div className="chart-holder" style={{display: this.state.showExtensions ? "inline-block" : "none"}}>
                <Doughnut data={this.state.fileTypePieData}/>
            </div>
            <div className="chart-holder" style={{display: this.state.showMediaType ? "inline-block" : "none"}}>
                <Doughnut data={this.state.mediaTypePieData}/>
            </div>
        </div>
    }
}

export default ProjectContentSummary;