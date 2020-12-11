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
10. Check the application configuration under `conf/application.conf`, specifically the `auth` section, to make sure it is
all valid.
11. Go to the `frontend/` directory and run `npm i; npm run dev` to start the frontend transpiler
12. Now you should be good to run the app, either via `sbt run` or through your IDE

----

## Old Authentication Setup

Vaultdoor was originally intended to run against an ldap-based authentication system, such as Active Directory. This is configured
in `application.conf`.

### ldaps

Secure ldap is recommended, as it not only encrypts the connection but protects against man-in-the-middle attacks.
In order to configure this, you will need to have a copy of the server's certificate and to create a trust store with it.
If your certificate is called `certificate.cer`, then the following commands will create a keystore:

```
$ mkdir -p /usr/share/projectlocker/conf
$ keytool -import -keystore /usr/share/projectlocker/conf/keystore.jks -file certificate.cer
[keytool will prompt for a secure passphrase for the keystore and confirmation to add the cert]
```

`keytool` should be provided by your java runtime environment.

In order to configure this, you need to adjust the `ldap` section in `application.conf`:

```
  ldapProtocol = "ldaps"
  ldapUseKeystore = true
  ldapPort = 636
  ldapHost0 = "adhost1.myorg.int"
  ldapHost1 = "adhost2.myorg.int"
  serverAddresses = ["adhost1.myorg.int","adhost2.myorg.int"]
  serverPorts = [ldapPort,ldapPort]
  bindDN = "aduser"
  bindPass = "adpassword"
  poolSize = 3
  roleBaseDN = "DC=myorg,DC=com"
  userBaseDN = "DC=myorg,DC=com"
  trustStore = "/usr/share/projectlocker/conf/keystore.jks"
  trustStorePass = "YourPassphraseHere"
  trustStoreType = "JKS"
  ldapCacheDuration = 600
  acg1 = "acg-name-1"
```

Replace `adhost*.myorg.int` with the names of your AD servers, `aduser` and `adpassword` with the username and password
to log into AD, and your DNs in `roleBaseDN` and `userBaseDN`.

### ldap

Plain unencrypted ldap can also be used, but is discouraged.  No keystore is needed, simply configure the `application.conf`
as above but use `ldapProtocol = "ldap"` and `ldapPort = 336` instead.

### none

Authentication can be disabled, if you are working on development without access to an ldap server.  Simply set
`ldapProtocol = "none"` in `application.conf`.  This will treat any session to be logged in with a username of `noldap`.

Fairly obviously, don't deploy the system like this!

