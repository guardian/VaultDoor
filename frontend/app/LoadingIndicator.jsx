import React, { useEffect } from "react";
import PropTypes from "prop-types";

/**
 * simple little loading indicator that puts a dot onto the end of the string every 1/10 second
 */
class LoadingIndicator extends React.Component {
  static propTypes = {
    messageText: PropTypes.string,
    blank: PropTypes.bool,
  };

  constructor(props) {
    super(props);

    this.state = {
      dots: "",
      timer: null,
    };

    this.timerTick = this.timerTick.bind(this);
  }

  componentDidMount() {
    const timerId = window.setInterval(this.timerTick, 100);
    this.setState({
      timer: timerId,
    });
  }

  componentWillUnmount() {
    if (this.state.timer) {
      window.clearInterval(this.state.timer);
    }
  }

  timerTick() {
    //old-skool 80 column display :D
    this.setState((prevState) =>
      prevState.dots.length > 72
        ? { dots: "." }
        : { dots: prevState.dots + "." }
    );
  }

  render() {
    return (
      <span className="loading">
        {(this.props.messageText && !this.props.blank) ?? "Loading"}{" "}
        {this.state.dots}
      </span>
    );
  }
}

export default LoadingIndicator;
