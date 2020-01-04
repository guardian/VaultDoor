import React from "react";
import ProjectLockerSearchBar from "./projectsearch/ProjectLockerSearchBar.jsx";

class ByProjectComponent extends React.Component {
    static propTypes = {
        projectLockerBaseUrl: PropTypes.string.isRequired
    };

    constructor(props){
        super(props);

        this.state = {
            loading: false,
            currentProjectSearch: ""
        };
    }

    render() {
        return <div className="windowpanel">
            <ProjectLockerSearchBar projectLockerBaseUrl={this.props.projectLockerBaseUrl}
                                    projectSelectionChanged={newProject=>this.setState({currentProjectSearch: newProject})}
                                    size={15}/>
        </div>
    }
}

export default ByProjectComponent;