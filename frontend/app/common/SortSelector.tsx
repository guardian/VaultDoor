import React, { ChangeEventHandler, ReactEventHandler } from "react";
import { Grid, MenuItem, Select } from "@material-ui/core";
import { SelectInputProps } from "@material-ui/core/Select/SelectInput";

interface SortSelectorProps {
  selectedField: string;
  sortOrder: string;
  onChange: (fieldName: string, sortOrder: string) => void;
}

const SortSelector: React.FC<SortSelectorProps> = (props) => {
  const sortSelectorChanged: SelectInputProps["onChange"] = (evt) => {
    props.onChange(evt.target.value as string, props.sortOrder);
  };

  const directionSelectorChanged: SelectInputProps["onChange"] = (evt) => {
    props.onChange(props.selectedField, evt.target.value as string);
  };

  return (
    <Grid container direction="row">
      <Grid item>
        <label htmlFor="sort-field-selector">Sort:</label>
        <Select
          id="sort-field-selector"
          value={props.selectedField}
          onChange={sortSelectorChanged}
        >
          //see FileListController
          <MenuItem value="MXFS_ARCHIVE_TIME">Archive time</MenuItem>
          <MenuItem value="MXFS_CREATION_TIME">Created time</MenuItem>
          <MenuItem value="MXFS_MODIFICATION_TIME">Modified time</MenuItem>
          <MenuItem value="MXFS_FILEEXT">File type</MenuItem>
          <MenuItem value="MXFS_FILENAME">File name</MenuItem>
          <MenuItem value="DPSP_SIZE">File size</MenuItem>
        </Select>
      </Grid>
      <Grid item>
        <Select
          id="sort-dir-selector"
          value={props.sortOrder}
          onChange={directionSelectorChanged}
        >
          <MenuItem value="Descending">
            {props.selectedField?.includes("TIME")
              ? "Most recent first"
              : "Descending"}
          </MenuItem>
          <MenuItem value="Ascending">
            {props.selectedField?.includes("TIME")
              ? "Oldest first"
              : "Ascending"}
          </MenuItem>
        </Select>
      </Grid>
    </Grid>
  );
};

export default SortSelector;
