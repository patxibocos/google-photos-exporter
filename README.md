# Google Photos GitHub exporter

## What is google-photos-github-exporter ❓

`google-photos-github-exporter` is an app that fetches every photo from Google Photos library and uploads it to GitHub.

## Build 🔨

**Java 8** is the single requirement to build and run. It can be easily installed using [SDKMAN!](https://sdkman.io/)

Once installed, the app must be built using the included Gradle wrapper:

```shell
./gradlew build
```

This will place a runnable Java jar under **build/libs** directory.

## Setup ⚙️

To successfully run this app, two things need to be setup first.  

### GitHub token 🐈‍⬛

This token will allow this app to push content to the GitHub repository that is configured to be used as storage. To create one, follow these steps:

1. Head to personal access tokens on GitHub => https://github.com/settings/tokens?type=beta
2. Click on `Generate new token`
3. Under repository access, select `Only select repositories` and choose the one where photos will be exported
4. On `Permissions` section, click on `Repository permissions` and choose `Read and write` access level for `Contents` permission:
    ![GitHub token permissions](screenshots/github-token-permissions.png)
5. Click on `Generate token`

### Google Photos OAuth 📷

Google Photos API uses OAuth so the user grants access to the given app. For this app to work, it is needed both client ID and secret for the OAuth app, and also a non-expiring refresh token that will be used to get a new access token every time the app runs.

In order to get a non-expiring refresh token, please follow the steps described on this StackOverflow post => https://stackoverflow.com/a/58741728

After completing the steps, you should get the client ID, client secret and refresh token (to be setup later as environment variables).

## Usage 📙

The app can be executed from the command line:

```shell
java -jar google-photos-github-exporter.jar -h
```

```shell
Usage: google-photos-github-exporter options_list
Options:
    --githubRepoOwner, -gro -> GitHub repository owner (always required) { String }
    --githubRepoName, -grn -> GitHub repository name (always required) { String }
    --itemTypes, -it [PHOTO, VIDEO] -> Item types to include { Value should be one of [photo, video] }
    --maxChunkSize, -mcs [2147483647] -> Max chunk size { Int }
    --help, -h -> Usage info
```

The app requires passing two mandatory arguments:

- **githubRepoOwner**: Owner of the repository where photos will be saved.
- **githubRepoName**: Name of the repository.

and the following **environment variables** to be present:

- **GITHUB_TOKEN**: GitHub personal access token with write access to the repo where photos will be stored
- **GOOGLE_PHOTOS_CLIENT_ID**: ID of the OAuth client 
- **GOOGLE_PHOTOS_CLIENT_SECRET**: secret of the OAuth client 
- **GOOGLE_PHOTOS_REFRESH_TOKEN**: a non expiring refresh token for the OAuth client
