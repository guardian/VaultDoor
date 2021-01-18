import React from "react";
import PropTypes from "prop-types";
import FilterableList from "../common/FilterableList.jsx";
import ProjectLockerLoginComponent from "./ProjectLockerLoginComponent.jsx";
import { authenticatedFetch } from "../auth";

class ProjectLockerSearchBar extends React.Component {
  static propTypes = {
    projectLockerBaseUrl: PropTypes.string.isRequired,
    projectSelectionChanged: PropTypes.func.isRequired,
    size: PropTypes.number.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      currentWorkingGroupUuid: null,
      currentCommissionVsid: null,
      currentProjectVsid: null,
      lastError: null,
      projectLockerLoggedIn: false,
      projectLockerUsername: "",
      knownWorkingGroups: [],
      commSearchCounter: 0,
      projSearchCounter: 0,
    };

    this.workingGroupFetchUrl = "/api/pluto/workinggroup";
    this.commissionFetchUrl = "/api/pluto/commission/list";
    this.projectFetchUrl = "/api/project/advancedsearch";
    this.workingGroupChanged = this.workingGroupChanged.bind(this);
    this.makeCommissionSearch = this.makeCommissionSearch.bind(this);
    this.makeProjectSearch = this.makeProjectSearch.bind(this);
    this.newPLLogin = this.newPLLogin.bind(this);
  }

  newPLLogin(username) {
    this.setState(
      { projectLockerLoggedIn: true, projectLockerUsername: username },
      () => this.initialWorkingGroupLoad()
    );
  }

  async checkPLLogin() {
    if (this.props.projectLockerBaseUrl === "") return;

    try {
      const response = await authenticatedFetch(
        this.props.projectLockerBaseUrl + "/api/isLoggedIn",
        { credentials: "include" }
      );
      const bodyContent = await response.json();

      return new Promise((resolve, reject) => {
        if (response.ok) {
          this.setState(
            {
              projectLockerLoggedIn: true,
              projectLockerUsername: bodyContent.uid,
            },
            () => resolve()
          );
        } else if (response.status === 403) {
          //403=>not logged in
          this.setState(
            { projectLockerLoggedIn: false, lastError: "Could not log in" },
            () => resolve()
          );
        } else {
          this.setState(
            {
              projectLockerLoggedIn: false,
              lastError: JSON.stringify(bodyContent),
            },
            () => resolve()
          );
        }
      });
    } catch (err) {
      return new Promise((resolve, reject) => reject(err));
    }
  }

  async initialWorkingGroupLoad() {
    if (this.props.projectLockerBaseUrl === "") return;
    const response = await fetch(
      this.props.projectLockerBaseUrl + this.workingGroupFetchUrl,
      { credentials: "include" }
    );
    const bodyText = await response.text();

    if (response.ok) {
      return new Promise((resolve, reject) => {
        const bodyContent = JSON.parse(bodyText);
        this.setState(
          {
            knownWorkingGroups: ProjectLockerSearchBar.workingGroupContentConverter(
              bodyContent
            ),
          },
          () => resolve()
        );
      });
    } else {
      return new Promise((resolve, reject) => {
        this.setState({ lastError: bodyText }, () => resolve());
      });
    }
  }

  componentDidMount() {
    this.checkPLLogin().catch((err) => {
      this.setState({
        lasatError:
          "Could not communicate with Projectlocker. Verify that the server is up and correctly configured for CORS usage.",
      });
    });
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    console.log(prevProps, this.props);

    if (this.props.projectLockerBaseUrl !== prevProps.projectLockerBaseUrl) {
      this.checkPLLogin().then(() => this.initialWorkingGroupLoad());
    }
  }

  workingGroupChanged(newWg) {
    console.log("working group changed: ", newWg);
    this.setState({
      currentWorkingGroup: newWg,
      commSearchCounter: this.state.commSearchCounter + 1,
      currentCommissionVsid: "",
      projSearchCounter: this.state.projSearchCounter + 1,
    });
  }

  static workingGroupContentConverter(incomingData) {
    console.log("workingGroupContentConverter: ", incomingData);
    return incomingData.result.map((entry) => {
      return { name: entry.name, value: entry.id };
    });
  }

  static commissionContentConverter(incomingData) {
    console.log("commissionContentConverter: ", incomingData);
    return incomingData.result.map((entry) => {
      return {
        name: entry.title,
        value: entry.siteId + "-" + entry.collectionId.toString(),
      };
    });
  }

  static projectContentConverter(incomingData) {
    console.log("projectContentConverter: ", incomingData);
    return incomingData.result.map((entry) => {
      return { name: entry.title, value: entry.vidispineId };
    });
  }

  makeCommissionSearch(enteredText) {
    console.log("makeCommissionSearch: ", enteredText);
    return {
      title: enteredText,
      match: "W_STARTSWITH",
      workingGroupId: parseInt(this.state.currentWorkingGroup),
    };
  }

  makeProjectSearch(enteredText) {
    return {
      query: {
        title: enteredText,
        match: "W_CONTAINS",
      },
      filter: {
        commissionId: this.state.currentCommissionVsid,
      },
    };
  }

  render() {
    if (!this.state.projectLockerLoggedIn) {
      return (
        <div className="search-bar">
          <p className="centered">
            You are not logged in to ProjectLocker. For advanced
            project/commission search please log in below
          </p>
          <ProjectLockerLoginComponent
            projectLockerBaseUrl={this.props.projectLockerBaseUrl}
            loginSuccess={this.newPLLogin}
          />
          <p className="centered information error">{this.state.lastError}</p>
        </div>
      );
    }

    return (
      <div className="search-bar">
        <div className="search-bar-element">
          <h3>Working Group</h3>
          <FilterableList
            onChange={this.workingGroupChanged}
            value={this.state.currentWorkingGroup}
            size={this.props.size}
            unfilteredContent={this.state.knownWorkingGroups}
          />
        </div>
        <div className="search-bar-element">
          <h3>Commission</h3>
          <FilterableList
            onChange={(updatedComm) =>
              this.setState({
                currentCommissionVsid: updatedComm,
                projSearchCounter: this.state.projSearchCounter + 1,
              })
            }
            value={this.state.currentCommissionVsid}
            size={this.props.size}
            unfilteredContentFetchUrl={
              this.props.projectLockerBaseUrl + this.commissionFetchUrl
            }
            makeSearchDoc={this.makeCommissionSearch}
            unfilteredContentConverter={
              ProjectLockerSearchBar.commissionContentConverter
            }
            triggerRefresh={this.state.commSearchCounter}
            allowCredentials={true}
          />
        </div>
        <div className="search-bar-element">
          <h3>Project</h3>
          <FilterableList
            onChange={(updatedProj) =>
              this.props.projectSelectionChanged(updatedProj)
            }
            value={this.state.currentProjectVsid}
            size={this.props.size}
            unfilteredContentFetchUrl={
              this.props.projectLockerBaseUrl + this.projectFetchUrl
            }
            makeSearchDoc={this.makeProjectSearch}
            unfilteredContentConverter={
              ProjectLockerSearchBar.projectContentConverter
            }
            triggerRefresh={this.state.projSearchCounter}
            allowCredentials={true}
          />
        </div>
        <p className="centered information">
          Logged in to projectlocker as {this.state.projectLockerUsername}
        </p>
      </div>
    );
  }
}

export default ProjectLockerSearchBar;
