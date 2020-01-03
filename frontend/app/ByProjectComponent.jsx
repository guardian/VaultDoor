import React from "react";
import FilterableList from "./common/FilterableList";

class ByProjectComponent extends React.Component {
    static propTypes = {
        projectLockerBaseUrl: PropTypes.string.isRequired
    };

    constructor(props){
        super(props);

        this.state = {
            loading: false,
            currentProjectSearch: "",
            currentWorkingGroup: "",
        };
    }

    render() {

    }
}

export default ByProjectComponent;