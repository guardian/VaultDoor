import React from "react";
import { FolderOpen } from "@material-ui/icons";

const TRUNC_START = 1; //truncate by removing the start
const TRUNC_MIDDLE = 2; //truncate by removing the middle
const TRUNC_END = 3; //truncate by removing the end

interface PathViewProps {
  pathParts: string[];
  limit: number | undefined;
  truncateMode: 1 | 2 | 3;
  stripStart: number | undefined;
}

const PathView: React.FC<PathViewProps> = (props) => {
  const truncateParts = () => {
    const pathParts = props.stripStart
      ? props.pathParts.slice(props.stripStart)
      : props.pathParts;

    switch (props.truncateMode) {
      case TRUNC_START:
        const truncateFrom = props.limit;
        return ["..."].concat(pathParts.slice(truncateFrom));
      case TRUNC_END:
        const truncateTo = props.limit;
        return pathParts.slice(0, truncateTo).concat(["..."]);
      default:
        const sectionLength = props.limit
          ? props.limit / 2
          : pathParts.length - 1;
        const firstPart = pathParts.slice(0, sectionLength + 1);
        const lastPart = ["..."].concat(
          props.pathParts.slice(props.pathParts.length - sectionLength + 1)
        );
        return firstPart.concat(lastPart);
    }
  };

  if (props.pathParts == null) return null;

  const truncated = props.limit ? props.pathParts.length > props.limit : false;
  const visibleParts = truncated ? truncateParts() : props.pathParts;
  return (
    <ul className="pathview">
      {visibleParts.map((part, idx) => {
        const indent = idx * 0.25 + 1;
        return (
          <li className="pathview" style={{ paddingLeft: indent + "em" }}>
            <FolderOpen />
            {part}
          </li>
        );
      })}
    </ul>
  );
};

export { TRUNC_START, TRUNC_MIDDLE, TRUNC_END };
export default PathView;
