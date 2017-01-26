# greenhorn

[![CircleCI](https://circleci.com/gh/Le6ow5k1/greenhorn.svg?style=svg)](https://circleci.com/gh/Le6ow5k1/greenhorn)

Bot that checks github PR's for changed dependencies in Gemfile.lock.

It analyzes changes made to Gemfile.lock file, determines which dependencies are changed, and posts a comment with list of changes and corresponding links to github compare page.

## Development

You will need Docker and Docker-compose installed.

Copy config file:

    cp profiles.clj.example profiles.clj

Then specify config variables.

Then run migrations:

    docker-compose run --rm app lein migrate

To start a web server for the application, run:

    docker-compose up web

## Test

    docker-compose run --rm app lein test

## Roadmap

  - [] Commits from gems diffs, possibly JIRA issues
  - [] Automatic updates of organization repos
  - [] Smart truncating of large comments
  - [] Github authentication
  - [] Removing pull info on merge and close

## License

Copyright © 2016 Konstantin Lazarev
