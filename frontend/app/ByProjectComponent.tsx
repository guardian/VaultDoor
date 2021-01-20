import React, { useState, useEffect } from "react";
import { RouteChildrenProps } from "react-router";
import { authenticatedFetch } from "./auth";
import NewVaultSelector from "./searchnbrowse/NewVaultSelector";
import ProjectLockerSearchBar from "./projectsearch/ProjectLockerSearchBar";
import ProjectContentSummary from "./projectsearch/ProjectContentSummary";
import { Grid, makeStyles } from "@material-ui/core";

interface ByProjectComponentWrapperProps {}

interface ByProjectComponentWrapperState {
  lastError?: string;
}

/**
 * this class is no more than a wrapper to implement an error boundary and prevent the component from breaking the entire page
 */
class ByProjectComponent extends React.Component<
  RouteChildrenProps<ByProjectComponentWrapperProps>,
  ByProjectComponentWrapperState
> {
  constructor(props: RouteChildrenProps<ByProjectComponentWrapperProps>) {
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
    console.error("ByProjectComponent failed: ", error);
    console.log(errorInfo);
  }

  render() {
    return <ProjectSearchBar {...this.props} />;
  }
}

interface ProjectSearchBarState {}

const useStyles = makeStyles({
  parentBox: {
    marginLeft: "3%",
    marginRight: "3%",
    width: "94%",
    overflow: "hidden",
  },
});

/**
 * the actual search bar is implemented here
 * @param props
 * @constructor
 */
const ProjectSearchBar: React.FC<RouteChildrenProps> = (props) => {
  const [loading, setLoading] = useState(false);
  const [currentProjectSearch, setCurrentProjectSearch] = useState<
    string | undefined
  >(undefined);
  const [projectLockerBaseUrl, setProjectLockerBaseUrl] = useState("");
  const [lastError, setLastError] = useState<string | undefined>(undefined);
  const [vaultId, setVaultId] = useState("");

  const classes = useStyles();

  function breakdownSearchParams() {
    const fullstring = props.location.search.slice(1);
    const parts = fullstring.split("&");
    const elems = parts.map((entry) => entry.split("="));
    return elems.reduce<Map<string, string>>((acc, elem) => {
      acc.set(elem[0], elem[1]);
      return acc;
    }, new Map<string, string>());
  }

  function setupCurrentSearch() {
    const searchParams = breakdownSearchParams();
    console.log(searchParams);
    const projectValue = searchParams.get("project");
    projectValue ? setCurrentProjectSearch(projectValue) : null;
  }

  async function loadFrontendConfig() {
    const response = await authenticatedFetch("/api/config", {});
    if (response.ok) {
      const content = await response.json();
      setProjectLockerBaseUrl(content.projectLockerBaseUrl);
    } else {
      const content = await response.text();
      setLastError(content);
    }
  }

  //set up the frontend config on load
  useEffect(() => {
    loadFrontendConfig().then(() => {
      setupCurrentSearch();
    });
  }, []);

  //if the current project search is updated, reflect it in the url
  useEffect(() => {
    if (currentProjectSearch) {
      props.history.push(`?project=${currentProjectSearch}`);
    } else {
      props.history.push("/byproject");
    }
  }, [currentProjectSearch]);

  return (
    <div className="windowpanel">
      <ProjectLockerSearchBar
        className={classes.parentBox}
        projectLockerBaseUrl={projectLockerBaseUrl}
        projectSelectionChanged={(newProject) =>
          setCurrentProjectSearch(newProject)
        }
        size={8}
      />
      <Grid
        container
        direction="row"
        justify="center"
        className={classes.parentBox}
      >
        <Grid item xs={9}>
          <NewVaultSelector
            currentvault={vaultId}
            vaultWasChanged={(newVaultId) => setVaultId(newVaultId)}
          />
        </Grid>
      </Grid>
      <ProjectContentSummary
        vaultId={vaultId}
        projectId={currentProjectSearch}
        plutoBaseUrl={projectLockerBaseUrl}
      />
    </div>
  );
};

export default ByProjectComponent;
