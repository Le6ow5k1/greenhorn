# greenhorn

[![CircleCI](https://circleci.com/gh/Le6ow5k1/greenhorn.svg?style=svg)](https://circleci.com/gh/Le6ow5k1/greenhorn)

Bot that checks github PS's for changed dependencies in Gemfile.lock.

## Development

You will need Docker and Docker-compose installed.

To start a web server for the application, run:

    docker-compose up web

## Test

    docker-compose run app lein test

## License

Copyright © 2016 FIXME
