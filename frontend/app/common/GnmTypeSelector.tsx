import React, {useEffect, useState} from "react";
import {authenticatedFetch} from "../auth";
import {createStyles, makeStyles, MenuItem, Select, SelectProps} from "@material-ui/core";

interface GnmTypeSelectorProps {
    selectedType:string;
    onChange:(newValue:string)=>void;
    onError?:(errorDesc:string)=>void;
    id?:string;
}

const useStyles = makeStyles({
    menuText: {
        textTransform: "capitalize"
    }
});

const GnmTypeSelector:React.FC<GnmTypeSelectorProps> = (props) => {
    const [knownTypes, setKnownTypes] = useState<string[]>([]);

    const classes = useStyles();

    useEffect(()=>{
        loadData()
            .catch(err=>{
                console.error("loadData crashed: ", err);
                if(props.onError) {
                    props.onError(`Could not load in gnm type values, see console for details`);
                }
            });
    }, []);

    const loadData = async ()=>{
        const response = await authenticatedFetch("/api/metadata/knownTypes",{});
        if(response.status==200){
            const content = await response.json() as ObjectListResponse<string>;
            setKnownTypes(content.entries);
        } else {
            const content = await response.text();
            console.error(`Could not load in gnm type values: server error ${response.status} (${content})`);
            if(props.onError) {
                props.onError(`Could not load in gnm type values: server error ${response.status}`);
            }
        }
    }

    const valueChanged:SelectProps['onChange'] = (evt)=> {
        props.onChange(evt.target.value as string);
    }

    return <Select value={props.selectedType} id={props.id} onChange={valueChanged} className={classes.menuText}>
        <MenuItem value="any">Any</MenuItem>
        {
            knownTypes.map(
                (t,idx)=><MenuItem key={idx} value={t} className={classes.menuText}>{t}</MenuItem>
            )
        }
    </Select>
}

export default GnmTypeSelector;