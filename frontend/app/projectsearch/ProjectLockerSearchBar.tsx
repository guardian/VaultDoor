import React, { useEffect, useState } from "react";
import { RouteChildrenProps } from "react-router";
import { authenticatedFetch } from "../auth";
import { Grid, makeStyles, Typography } from "@material-ui/core";
import FilterableList from "../common/FilterableList";

interface ProjectLockerSearchBarProps {
  projectLockerBaseUrl: string;
  projectSelectionChanged: (newProject: string | undefined) => void;
  size: number;
  className?: string;
}

/**
 * wrapper component that contains an error boundary
 */
class ProjectLockerSearchBar extends React.Component<
  ProjectLockerSearchBarProps,
  any
> {
  constructor(props: ProjectLockerSearchBarProps) {
    super(props);

    this.state = {
      lastError: undefined,
    };
  }

  static getDerivedStateFromError(error: Error) {
    return {
      lastError: error.toString(),
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("ProjectLockerSearchBar failed: ", error);
    console.log(errorInfo);
  }

  render() {
    return this.state.lastError ? (
      <Typography>{this.state.lastError}</Typography>
    ) : (
      <ProjectLockerSearchBarImplementation {...this.props} />
    );
  }
}

const useStyles = makeStyles({});

const ProjectLockerSearchBarImplementation: React.FC<ProjectLockerSearchBarProps> = (
  props
) => {
  const [currentWorkingGroupId, setCurrentWorkingGroupId] = useState<
    string | undefined
  >(undefined);
  const [currentCommissionVsid, setCurrentCommissionVsid] = useState<
    string | undefined
  >(undefined);
  const [currentProjectVsid, setCurrentProjectVsid] = useState<
    string | undefined
  >(undefined);
  const [lastError, setLastError] = useState<string | undefined>(undefined);
  const [projectLockerLoggedIn, setProjectLockerLoggedIn] = useState(false);
  const [projectLockerUsername, setProjectLockerUsername] = useState("");
  const [knownWorkingGroups, setKnownWorkingGroups] = useState<NameValuePair[]>(
    []
  );
  const [commSearchCounter, setCommSearchCounter] = useState<number>(0);
  const [projSearchCounter, setProjSearchCounter] = useState<number>(0);

  const workingGroupFetchUrl = "/api/pluto/workinggroup";
  const commissionFetchUrl = "/api/pluto/commission/list";
  const projectFetchUrl = "/api/project/list";

  const classes = useStyles();

  async function checkPLLogin() {
    if (props.projectLockerBaseUrl === "") {
      console.error("No project locker base URL set in the configuration");
      return;
    }

    try {
      const response = await authenticatedFetch(
        props.projectLockerBaseUrl + "/api/isLoggedIn",
        { credentials: "include" }
      );
      const bodyContent = await response.json();

      if (response.ok) {
        setProjectLockerUsername(bodyContent.uid);
        setProjectLockerLoggedIn(true);
      } else if (response.status === 403) {
        setProjectLockerLoggedIn(false);
        setLastError("Could not log in");
      } else {
        setLastError(JSON.stringify(bodyContent));
        setProjectLockerLoggedIn(false);
      }
    } catch (err) {
      setLastError("Could not contact pluto-core, please see browser console");
      return new Promise((resolve, reject) => reject(err));
    }
  }

  /**
   * callback function for FilterableList that takes content from a PlutoWorkingGroupResponse and converts it into
   * name/value pairs for the list
   * @param incomingData the PlutoWorkingGroupResponse
   */
  const workingGroupContentConverter: ValueConverterFunc<PlutoWorkingGroupResponse> = (
    incomingData
  ) => {
    console.log("workingGroupContentConverter: ", incomingData);
    return incomingData.result.map((entry) => {
      return { name: entry.name, value: entry.id };
    });
  };

  /**
   * callback function for FilterableList that takes content from a PlutoCommissionResponse and converts it into
   * name/value pairs for the list
   * @param incomingData the PlutoCommissionResponse
   */
  const commissionContentConverter: ValueConverterFunc<PlutoCommissionResponse> = (
    incomingData
  ) => {
    return incomingData.result.map((entry) => {
      return {
        name: entry.title,
        value: entry.id.toString(),
      };
    });
  };

  /**
   * callback function for FilterableList that takes content from a PlutoProjectResponse and converts it into
   * name/value pairs for the list
   * @param incomingData the PlutoProjectResponse
   */
  const projectContentConverter: ValueConverterFunc<PlutoProjectResponse> = (
    incomingData
  ) => {
    return incomingData.result.map((entry) => {
      return { name: entry.title, value: entry.id.toString() };
    });
  };

  /**
   * function that is called at startup to load in the working groups from pluto-core and store them in the component
   * state
   */
  async function initialWorkingGroupLoad() {
    if (props.projectLockerBaseUrl === "") {
      console.log("ProjectLockerSearchBar can't load initial working group because projectLockerBaseUrl is not set");
      return;
    }

    try {
      const response = await authenticatedFetch(
        props.projectLockerBaseUrl + workingGroupFetchUrl,
        { credentials: "include" }
      );
      const bodyText = await response.text();

      if (response.ok) {
        const bodyContent = JSON.parse(bodyText);
        setKnownWorkingGroups(
          workingGroupContentConverter(bodyContent as PlutoWorkingGroupResponse)
        );
      } else {
        setLastError(bodyText);
      }
    } catch (err) {
      console.error("Could not load initial working group: ", err);
      setLastError(err.toString);
    }
  }

  /**
   * once we have a base URL set, then verify that we can connect.
   * checkPLLogin sets projectLockerLoggedIn to true/false and sets lastError
   * if it can't connect
   */
  useEffect(() => {
    checkPLLogin();
  }, [props.projectLockerBaseUrl]);

  /**
   * once we have a valid connection, load in the working groups
   */
  useEffect(() => {
    if (projectLockerLoggedIn) {
      initialWorkingGroupLoad();
    }
  }, [projectLockerLoggedIn]);

  /**
   * update the commission search box if the working group id changes
   */
  useEffect(() => {
    setCommSearchCounter((prevValue) => prevValue + 1);
    setCurrentCommissionVsid(undefined);
    setProjSearchCounter((prevValue) => prevValue + 1);
  }, [currentWorkingGroupId]);

  /**
  update the project search box if the commission id changes
   */
  useEffect(() => {
    setCurrentProjectVsid(undefined);
    setProjSearchCounter((prevValue) => prevValue + 1);
  }, [currentCommissionVsid]);

  /**
   * tell the parent if the project selection changes
   */
  useEffect(() => {
    props.projectSelectionChanged(currentProjectVsid);
  }, [currentProjectVsid]);

  /**
   * this is a callback for FilterableList which generates a commission search request based on the contents
   * of the filterable list search box (passed in as enteredText) and the currently selected working group id
   * @param enteredText content of filterable list search box
   */
  const makeCommissionSearch = (enteredText: string) => {
    return currentWorkingGroupId
      ? {
          title: enteredText,
          match: "W_CONTAINS",
          workingGroupId: parseInt(currentWorkingGroupId),
        }
      : null;
  };

  /**
   * this is a callback for FilterableList which generates a project search request based on the contents of the
   * filterable list seach box (passed in as enteredText) and the currently selected commission id
   * @param enteredText content of the filterable list search box
   */
  const makeProjectSearch = (enteredText: string) => {
    return currentCommissionVsid
      ? {
          title: enteredText,
          match: "W_CONTAINS",
          commissionId: parseInt(currentCommissionVsid),
        }
      : null;
  };

  /**
   * render the search bar
   */
  return lastError ? (
    <Typography className={props.className}>{lastError}</Typography>
  ) : (
    <Grid container spacing={3} className={props.className}>
      <Grid item xs={4}>
        <h3>Working Group</h3>
        <FilterableList
          value={currentWorkingGroupId}
          size={props.size}
          unfilteredContent={knownWorkingGroups}
          onChange={(newWorkingGroup: string) => {
            console.log("new working group set: ", newWorkingGroup);
            setCurrentWorkingGroupId(newWorkingGroup);
          }}
        />
      </Grid>
      <Grid item xs={4}>
        <h3>Commission</h3>
        <FilterableList
          onChange={(newComm) => {
            console.log("new commission id set: ", newComm);
            setCurrentCommissionVsid(newComm);
          }}
          size={props.size}
          value={currentCommissionVsid}
          unfilteredContentFetchUrl={
            props.projectLockerBaseUrl + commissionFetchUrl
          }
          makeSearchDoc={makeCommissionSearch}
          unfilteredContentConverter={commissionContentConverter}
          triggerRefresh={commSearchCounter}
          allowCredentials={true}
        />
      </Grid>
      <Grid item xs={4}>
        <h3>Project</h3>
        <FilterableList
          size={props.size}
          value={currentProjectVsid}
          unfilteredContentFetchUrl={
            props.projectLockerBaseUrl + projectFetchUrl
          }
          makeSearchDoc={makeProjectSearch}
          unfilteredContentConverter={projectContentConverter}
          triggerRefresh={projSearchCounter}
          allowCredentials={true}
          onChange={(newProj) => {
            console.log("new project id set: ", newProj);
            setCurrentProjectVsid(newProj);
          }}
        />
      </Grid>
    </Grid>
  );
};
export default ProjectLockerSearchBar;
