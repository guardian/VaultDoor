#VaultDoor

This app implements an alternative method for getting content from an ObjectMatrix appliance.
It's been developed to ease the load on our Vidispine application server, which has been having to 
stream the media out itself in the past.

## Usage

Simply request to `/stream/{urlencoded-objectmatrix-url}` to obtain data:

- a **HEAD** request returns the metadata that would be returned for the streaming operation.
This is useful e.g. for getting the total content length before proceeding with a download operation
- a **GET** request will stream the content down the HTTP connection using Akka Streams.  The **Range** header is
not just supported but encouraged; although any range other than `bytes` will return a 400 error.

Going to the root url in a browser will display a basic management dashboard.

## Session cookie setup

Logins are persisted by using session cookies. This is controlled by the `play.http.session` section of `application.conf`.

When deploying, you should ensure that the `domain = ` setting is configured to be the domain within which you are deploying,
to prevent cookie theft.  It's also recommended to serve via https and set `secure = true` (but this could be problematic if you're
only implementing https to the loadbalancer)

## Authentication Setup

Projectlocker is intended to run against an ldap-based authentication system, such as Active Directory. This is configured
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
