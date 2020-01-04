import React from 'react';
import PropTypes from 'prop-types';
import FilterableList from "../common/FilterableList.jsx";

class ProjectLockerSearchBar extends React.Component {
    static propTypes = {
        projectLockerBaseUrl: PropTypes.string.isRequired,
        projectSelectionChanged: PropTypes.func.isRequired,
        size: PropTypes.number.isRequired
    };

    constructor(props){
        super(props);

        this.state = {
            loading: false,
            currentWorkingGroupUuid: null,
            currentCommissionVsid: null,
            currentProjectVsid: null,
            lastError: null
        };

        this.workingGroupFetchUrl = this.props.projectLockerBaseUrl + "/api/pluto/workinggroup";
        this.commissionFetchUrl = this.props.projectLockerBaseUrl + "/api/pluto/commission/list";
        this.projectFetchUrl = this.props.projectLockerBaseUrl + "/api/project/advancedsearch";
        this.workingGroupChanged = this.workingGroupChanged.bind(this);
        this.makeCommissionSearch = this.makeCommissionSearch.bind(this);
        this.makeProjectSearch = this.makeProjectSearch.bind(this);
    }

    workingGroupChanged(newWg){
        console.log("working group changed: ", newWg);
        this.setState({currentWorkingGroup: newWg});
    }

    static workingGroupContentConverter(incomingData){
        console.log("workingGroupContentConverter: ", incomingData);
        return incomingData.map(entry=>{return {name: entry.name, value: entry.uuid}})
    }

    static commissionContentConverter(incomingData){
        console.log("commissionContentConverter: ", incomingData);
        return incomingData.map(entry=>{return {name: entry.siteId + "-" + entry.collectionId.toString, value: entry.title}})
    }

    static projectContentConverter(incomingData){
        console.log("projectContentConverter: ", incomingData);
        return incomingData.map(entry=>{return {name: entry.projectTitle, value: entry.vidispineProjectId}})
    }

    makeCommissionSearch(enteredText) {
        console.log("makeCommissionSearch: ", enteredText);
        return {
            title: enteredText,
            wildcard: "W_STARTSWITH",
            workingGroupId: this.state.currentWorkingGroup
        }
    }

    makeProjectSearch(enteredText) {
        console.log("makeProjectSearch: ", enteredText);
        return {
            query: {
                title: enteredText,
                wildcard: "W_CONTAINS"
            },
            filter: {
                commissionId: this.state.currentCommissionVsid,
                workingGroupId: this.state.currentWorkingGroupUuid
            }
        }
    }

    render() {
        return <div className="search-bar">
            <div style={{display: "inline", marginRight: "1em"}}>
                <FilterableList onChange={this.workingGroupChanged}
                                value={this.state.currentWorkingGroup}
                                size={this.props.size}
                                unfilteredContentFetchUrl={this.workingGroupFetchUrl}
                                unfilteredContentConverter={ProjectLockerSearchBar.workingGroupContentConverter}
                />
            </div>
            <div style={{display: "inline", marginRight: "1em"}}>
                <FilterableList onChange={updatedComm=>this.setState({currentCommissionVsid: updatedComm})}
                                value={this.state.currentCommissionVsid}
                                size={this.props.size}
                                unfilteredContentFetchUrl={this.commissionFetchUrl}
                                makeSearchDoc={this.makeCommissionSearch}
                                unfilteredContentConverter={ProjectLockerSearchBar.commissionContentConverter}
                />
            </div>
            <div style={{display: "inline", marginRight: "1em"}}>
                <FilterableList onChange={updatedProj=>this.props.projectSelectionChanged(updatedProj)}
                                value={this.state.currentProjectVsid}
                                size={this.props.size}
                                unfilteredContentFetchUrl={this.projectFetchUrl}
                                makeSearchDoc={this.makeProjectSearch}
                                unfilteredContentConverter={ProjectLockerSearchBar.projectContentConverter}
                />
            </div>
        </div>
    }
}

export default ProjectLockerSearchBar;