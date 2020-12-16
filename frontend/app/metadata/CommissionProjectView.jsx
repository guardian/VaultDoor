import React from 'react';
import PropTypes from 'prop-types';

/**
 * component to display working group->commission->project->master "breadcrumb"
 */
class CommissionProjectView extends React.Component {
    static propTypes = {
        entry: PropTypes.object,
        horizontal: PropTypes.bool,
        clickable: PropTypes.bool,
        onProjectClicked: PropTypes.func
    };

    constructor(props){
        super(props);
        this.clickedProject = this.clickedProject.bind(this);
    }

    shouldDisplay(){
        if(!this.props.entry) return false;
        if(!this.props.entry.gnmMetadata) return false;

        if(!this.props.entry.gnmMetadata.type) return false;
        return true;
    }

    clickedProject() {
        if(this.props.clickable && this.props.onProjectClicked) this.props.onProjectClicked();
    }


    render() {
        if(!this.props.entry || !this.props.entry.gnmMetadata) return <div className="comm-project-locator"/>;

        const meta = this.props.entry.gnmMetadata;

        const extraProps = this.props.horizontal ? {display: "inline", marginRight: "1em"} : {};

        return <div className="comm-project-locator" style={{display: this.shouldDisplay() ? "inline-block" : "none"}}>
            <ul className="comm-project-locator silent-list">
                <li style={extraProps}>{meta.workingGroupName ? meta.workingGroupName : "(no working group)"}</li>
                <li style={extraProps}><img src="assets/images/icon_commission.png" alt="commission" className="inline-bullet-image"/>{meta.commissionName}</li>
                <li style={extraProps}><img src="assets/images/icon_project.png" alt="project" className="inline-bullet-image"/>
                    <a onClick={this.clickedProject} className={this.props.clickable ? "clickable" : ""}>{meta.projectName}</a>
                </li>
                <li style={{display: meta.masterName ? "inherit" : "none"}}>
                    <img src="assets/images/icon_master.png" alt="master" className="inline-bullet-image"/>{meta.masterName}
                </li>
            </ul>
        </div>
    }
}

export default CommissionProjectView;