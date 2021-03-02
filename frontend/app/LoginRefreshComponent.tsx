import React, {useState, useEffect, useRef} from "react";
import {JwtDataShape} from "./DecodedProfile";
import {refreshLogin} from "./OAuth2Helper";

interface LoginComponentProps {
    refreshToken?: string;
    checkInterval?:number;
    loginData: JwtDataShape;
    onLoginRefreshed?: ()=>void;
    onLoginCantRefresh?: (reason:string)=>void;
    onLoginExpired: ()=>void;
    onLoggedOut?: ()=>void;
    overrideRefreshLogin?: (tokenUri:string)=>Promise<void>;    //only used for testing
    tokenUri: string;
}

const LoginRefreshComponent:React.FC<LoginComponentProps> = (props) => {
    const [refreshInProgress, setRefreshInProgress] = useState<boolean>(false);
    const [refreshFailed, setRefreshFailed] = useState<boolean>(false);
    const [refreshed, setRefreshed] = useState<boolean>(false);
    const [loginExpiryCount, setLoginExpiryCount] = useState<string>("");

    let loginDataRef = useRef(props.loginData);
    const tokenUriRef = useRef(props.tokenUri);
    const overrideRefreshLoginRef = useRef(props.overrideRefreshLogin);

    useEffect(()=>{
        const intervalTimerId = window.setInterval(checkExpiryHandler, props.checkInterval ?? 60000);

        return (()=>{
            console.log("removing checkExpiryHandler")
            window.clearInterval(intervalTimerId);
        })
    }, []);

    useEffect(()=>{
        console.log("refreshFailed was toggled to ", refreshFailed);
        if(refreshFailed) {
            console.log("setting countdown handler");
            const intervalTimerId = window.setInterval(updateCountdownHandler, 1000);
            return (()=>{
                console.log("cleared countdown handler");
                window.clearInterval(intervalTimerId);
            })
        }
    }, [refreshFailed]);

    useEffect(()=>{
        loginDataRef.current = props.loginData;
    }, [props.loginData]);

    /**
     * called periodically every second once a refresh has failed to alert the user how long they have left
     */
    const updateCountdownHandler = () => {
        const nowTime = new Date().getTime() / 1000; //assume time is in seconds
        const expiry = loginDataRef.current.exp;
        const timeToGo = expiry - nowTime;

        if(timeToGo>1) {
            setLoginExpiryCount(`expires in ${Math.ceil(timeToGo)}s`);
        } else {
            if(props.onLoginExpired) props.onLoginExpired();
            setLoginExpiryCount("has expired");
        }
    }

    /**
     * lightweight function that is called every minute to verify the state of the token
     * it returns a promise that resolves when the component state has been updated. In normal usage this
     * is ignored but it is used in testing to ensure that the component state is only checked after it has been set.
     */
    const checkExpiryHandler = () => {
        if (loginDataRef.current) {
            const nowTime = new Date().getTime() / 1000; //assume time is in seconds
            //we know that it is not null due to above check
            const expiry = loginDataRef.current.exp;
            const timeToGo = expiry - nowTime;

            if (timeToGo <= 120) {
                console.log("less than 2mins to expiry, attempting refresh...");
                setRefreshInProgress(true);

                let refreshedPromise;

                if(overrideRefreshLoginRef.current){
                    refreshedPromise = overrideRefreshLoginRef.current(tokenUriRef.current);
                }  else {
                    refreshedPromise = refreshLogin(tokenUriRef.current);
                }

                refreshedPromise.then(()=>{
                    console.log("Login refreshed");
                    setRefreshInProgress(false);
                    setRefreshFailed(false);
                    setRefreshed(true);

                    if(props.onLoginRefreshed) props.onLoginRefreshed();
                    window.setTimeout(()=>setRefreshed(false), 5000);   //show success message for 5s
                }).catch(errString=>{
                    if(props.onLoginCantRefresh) props.onLoginCantRefresh(errString);
                    setRefreshFailed(true);
                    setRefreshInProgress(false);
                    updateCountdownHandler();
                    return;
                })
            }
        } else {
            console.log("no login data present for expiry check");
        }
    };

    return (
        <>
        </>
    )
}

export default LoginRefreshComponent;
