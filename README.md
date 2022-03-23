#VaultDoor

## What is Vaultdoor?

Vaultdoor is the "restore" in our nearline-backup-and-restore process. It's designed to rely on as few external components
as possible, storing all metadata within the Objectmatrix appliances, so it can act as a "failsafe" in a DR scenario.

Under the hood, this is a standard Play Framework webapp using Play 2.7 and Java 1.8 on Scala 2.12 with a frontend
provided by ReactJS.

**JAVA COMPATIBILITY NOTE** - at the time of writing, the MatrixStore librarie do NOT support any java higher than
1.8, so you should not attempt to run under a higher version.  We usually run on OpenJDK.

---

## How does it work?

Media is pushed onto the nearline storage appliances by manual-media-backup and the applicances themselves replicate
cross-site.
The backup process also collects a whole load of metadata, including the commission, project and working group that
the given media belongs to.

It's therefore possible to perform a search on the appliance for a given project ID and pull all of that content back,
even if the entire primary data centre has disappeared.

Download of multiple media files is performed via the Archivehunter download protocol, with client-side implementations
at https://github.com/guardian/plutohelperagent (Mac desktop) and https://github.com/guardian/autopull (cross-platform
commandline).

---

## Authentication

Vaultdoor uses OAuth2 for authentication.  The authentication dance is performed by the frontend and the backend relies
on signed JWTs presented as bearer-token authentication in order to validate the user.

See the seperate (forthcoming) readme for more details on the authentication process.

### Signing requests for server->server interactions

Vaultdoor supports HMAC signing of requests for server-server actions.
In order to use this, you must:

- provide a base64 encoded SHA-384 checksum of your request's content in a header called `X-Sha384-Checksum`
- ensure that an HTTP date is present in a header called `Date`
- ensure that the length of your body content is present in a header called `Content-Length`. If there is no body then this value should be 0.
- provide a signature in a header called 'Authorization'.  This should be of the form `{uid}:{auth}`, where {uid} is a user-provided
  identifier of the client and {auth} is the signature

The signature should be calculated like this:

- make a string of the contents of the Date, Content-Length and Checksum headers separated by newlines followed by the
  request method and URI path (not query parts) also separated by newlines.
- use the server's shared secret to calculate an SHA-384 digest of this string, and base64 encode it
- the server performs the same calculation (in `auth/HMAC.scala`) and if the two signatures match then you are in.
- if you have troubles, turn on debug at the server end to check the string_to_sign and digests

There is a working example of how to do this in Python in `scripts/test_hmac_auth.py`

---

## Development setup

1. You'll need an Oauth2 identity provider in order to perform login.  I'd recommend grabbing
   https://gitlab.com/codmill/customer-projects/guardian/prexit-local and following the instructions there to set up
   Keycloak within minikube
2. Once you've got minikube and keycloak certified and running, you're ready to configure it. Log in to https://keycloak.local
   as `admin`.
3. Click 'Administration Console' in the keycloak man window then click 'Clients' in the lefthand menu
4. Click 'Create' at the top-right of the list.  Client ID is `vaultdoor`, you don't need to change anything else. Click 'Save'.
5. Click 'Edit' next to the newly created 'vaultdoor' entry in the clients list
6. Under 'Valid Redirect URIs', enter http://localhost:9000/oauth2/callback
7. Under 'Web Origins', enter '+' (a single plus symbol)
8. Then hit Save.
9. Go to 'Realm Settings' in the left-hand menu, then the 'Keys' tab. Download the Certificate for the RS256 key and save it to
`keycloak-local.pem` in the root of your checked-out vaultdoor repo (this path is already gitignored)
10. Go to 'Clients' in the left-hand menu, select 'vaultdoor', then the 'Mappers' tab.
11. Click 'Create' in the top-right. Set Name to 'multimedia_admin', Mapper Type to 'Hardcoded claim', Token Claim Name to
'multimedia_admin', Claim value to 'true', and Claim JSON Type to 'String'. Click 'Save'.
12. Check the application configuration under `conf/application.conf`, specifically the `auth` section, to make sure it is
all valid.
13. Go to the `frontend/` directory and run `npm i; npm run dev` to start the frontend transpiler
14. Now you should be good to run the app, either via `sbt run` or through your IDE

----
