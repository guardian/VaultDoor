import React from 'react';
import PropTypes from 'prop-types';

class RootComponent extends React.Component {
    static propTypes = {
        onLoggedIn: PropTypes.func.isRequired,
        onLoggedOut: PropTypes.func.isRequired,
        currentUsername: PropTypes.string,
        isLoggedIn: PropTypes.bool.isRequired
    };

    render() {
        return(<div>
            {
                this.props.isLoggedIn ? <p>You are currently logged in as {this.props.currentUsername}</p> : <p>Not logged in, oauth init not implemented yet</p>
            }
        </div>);
    }
}

export default RootComponent;