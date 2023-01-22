# Google Photos exporter

## What is google-photos-exporter ❓

This is an app that fetches every photo from Google Photos library and uploads it to **Dropbox**, **GitHub**, **Box** or **OneDrive**.

It is shipped as a GitHub action, but it can be easily built and run by yourself.

## GitHub action 🚀

First of all, it is required to setup Google Photos auth to get a client id, client secret and a non expiring refresh
token. Please follow the steps in [Google Photos OAuth](#google-photos-oauth-).

Then, you need to configure auth for GitHub, Dropbox, Box or OneDrive:

- [**GitHub** token](#github-token-): follow the steps to create a personal token
- [**Dropbox** OAuth](#dropbox-oauth-): follow the steps to get OAuth data
- [**Box** OAuth](#box-oauth-): follow the steps to get OAuth data
- [**OneDrive** OAuth](#onedrive-oauth-): follow the steps to get OAuth data

### Usage 📕

The mandatory fields are `exporter` (**github**, **dropbox**, **box** or **onedrive**), `googlePhotosClientId`, `googlePhotosClientSecret` and `googlePhotosRefreshToken`.
Additionally there are exporter dependant mandatory fields.

- For **GitHub** => `githubAccessToken`, `githubRepositoryOwner` and `githubRepositoryName`
- For **Dropbox** => `dropboxAppKey`, `dropboxAppSecret` and `dropboxRefreshToken`
- For **Box** => `boxClientId`, `boxClientSecret` and `boxUserId`
- For **OneDrive** => `onedriveClientId`, `onedriveClientSecret` and `onedriveRefreshToken`

```yaml
- uses: patxibocos/google-photos-exporter@v1
  with:
    # Where to upload the photos (must be dropbox, github, box or onedrive) 
    exporter:
    # (Optional) Item types to filter (must be photo or video)
    itemType:
    # (Optional) Base path to upload the photos
    prefixPath:
    # (Optional) Max file size (in MB) to upload. In case a file is larger, it will be zipped and splitted
    maxChunkSize:
    # ID of the item to use as offset (not included)
    offsetId:
    # LocalDate pattern to use for the path of the item
    datePatternPath:
    # Name of the file where last successful item ID will be stored
    syncFileName:
    # Timeout for the runner    
    timeout:
    # ID of the last synced item
    lastSyncedItem:
    # Timeout for the requests
    requestTimeout:
    # Google Photos client ID
    googlePhotosClientId:
    # Google Photos client secret
    googlePhotosClientSecret:
    # Google Photos refresh token
    googlePhotosRefreshToken:
    # GitHub access token
    githubAccessToken:
    # GitHub repository owner
    githubRepositoryOwner:
    # GitHub repository name
    githubRepositoryName:
    # Dropbox app key
    dropboxAppKey:
    # Dropbox app secret
    dropboxAppSecret:
    # Dropbox refresh token
    dropboxRefreshToken:
    # Box client ID
    boxClientId:
    # Box client secret
    boxClientSecret:
    # Box user ID
    boxUserId:
    # OneDrive client ID
    onedriveClientId:
    # OneDrive client secret
    onedriveClientSecret:
    # OneDrive refresh token
    onedriveRefreshToken:
```

## Auth setup 👮‍♀️

To successfully run this app, a few things need to be setup first.

### Google Photos OAuth 📷

Google Photos API uses OAuth so the user grants access to the given app. For this app to work, it is needed both client
ID and secret for the OAuth app, and also a non-expiring refresh token that will be used to get a new access token every
time the app runs.

In order to get a non-expiring refresh token, please follow the steps described on this StackOverflow
post => https://stackoverflow.com/a/58741728

After completing the steps, you should get the client ID, client secret and refresh token (to be setup later as
environment variables).

### GitHub token 🐱

This token will allow this app to push content to the GitHub repository that is configured to be used as storage. To
create one, follow these steps:

1. Head to personal access tokens on GitHub => https://github.com/settings/tokens?type=beta
2. Click on `Generate new token`
3. Under repository access, select `Only select repositories` and choose the one where photos will be exported
4. On `Permissions` section, click on `Repository permissions` and choose `Read and write` access level for `Contents`
   permission:
   ![GitHub token permissions](screenshots/github-token-permissions.png)
5. Click on `Generate token`

### Dropbox OAuth 💧

1. Go to App Console => https://www.dropbox.com/developers/apps
2. Click on `Create app`
3. For API, choose `Scoped access`
4. On type of access, select `App folder` (unless you want to store the content out of the app folder)
5. Give any desired name to the app
6. Follow the
   steps [here](https://www.codemzy.com/blog/dropbox-long-lived-access-refresh-token#how-can-i-get-a-refresh-token-manually)
   to generate a **long lived refresh token**
7. Get app key and secret from app settings, and refresh token from the previous step

### Box OAuth 📦

1. Create a New App => https://app.box.com/developers/console/newapp
2. Choose `Custom App`
3. Choose `Server Authentication (Client Credentials Grant)`
4. Under Configuration -> Application Scopes -> Check ✔️`Write all files and folders stored in Box`
5. Under Configuration -> Advanced Features -> Check ✔️`Generate user access token`
6. Get client ID and Secret
7. Go to your account (https://app.box.com/account) and grab your Account ID. This is the user ID
8. Under Authorization -> Click on `Review and Submit`
9. Authorize the app

### OneDrive OAuth ☁️

1. Follow the steps described in => https://learn.microsoft.com/en-us/graph/auth-v2-user?view=graph-rest-1.0
    1. When setting the scopes, set `offline_access files.readwrite`. The first one will allow getting new access
       tokens. The later is required to upload photos/videos and also for reading the sync file.

## Build on your own 📙

**Java 8** is the single requirement to build and run. It can be easily installed using [SDKMAN!](https://sdkman.io/)

Once installed, the app must be built using the included Gradle wrapper:

```shell
./gradlew shadowJar
```

This will place a runnable Java jar under **build/libs** directory.

The app can be executed from the command line through a rich CLI:

```shell
java -jar google-photos-exporter.jar -h
```

```shell
Usage: google-photos-exporter options_list
Subcommands: 
    github - GitHub exporter
    dropbox - Dropbox exporter
    box - Box exporter
    box - OneDrive exporter

Options: 
    --itemTypes, -it [PHOTO, VIDEO] -> Item types to include { Value should be one of [photo, video] }
    --maxChunkSize, -mcs -> Max chunk size when uploading to GitHub { Int }
    --prefixPath, -pp [] -> Prefix path to use as parent path for content { String }
    --offsetId, -oi -> ID of the item to use as offset (not included) { String }
    --datePathPattern, -dpp [yyyy/MM/dd] -> LocalDate pattern to use for the path of the item { String }
    --syncFileName, -sfn [last-synced-item] -> Name of the file where last successful item ID will be stored { String }
    --timeout, -to -> Timeout for the runner { String }
    --lastSyncedItem, -lsi -> ID of the last synced item { String }
    --requestTimeout, -rto -> Timeout for the requests { String }
    --help, -h -> Usage info
```

The single argument that needs to be passed is the exporter to be used, which must be `dropbox` or `github`

The required **environment variables** to be passed are:

- **GOOGLE_PHOTOS_CLIENT_ID**: ID of the OAuth client
- **GOOGLE_PHOTOS_CLIENT_SECRET**: secret of the OAuth client
- **GOOGLE_PHOTOS_REFRESH_TOKEN**: a non expiring refresh token for the OAuth client

Depending on the exporter more environment variables are needed:

- For **Dropbox**:
    - **DROPBOX_APP_KEY**: app key of the OAuth app
    - **DROPBOX_APP_SECRET**: app secret of the OAuth app
    - **DROPBOX_REFRESH_TOKEN**: refresh token of the OAuth app
- For **GitHub**:
    - **GITHUB_REPOSITORY_OWNER**: owner of the repository where photos will be saved
    - **GITHUB_REPOSITORY_NAME**: name of the repository
    - **GITHUB_ACCESS_TOKEN**: personal access token with write access to the repo where photos will be stored
- For **Box**:
    - **BOX_CLIENT_ID**: client ID of the OAuth app
    - **BOX_CLIENT_SECRET**: client secret of the OAuth app
    - **BOX_USER_ID**: user ID of the user that will be used to upload the content
- For **OneDrive**:
    - **ONEDRIVE_CLIENT_ID**: client ID of the OAuth app
    - **ONEDRIVE_CLIENT_SECRET**: client secret of the OAuth app
    - **ONEDRIVE_REFRESH_TOKEN**: refresh token of the OAuth app