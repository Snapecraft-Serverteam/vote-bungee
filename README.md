![Java CI](https://github.com/Snapecraft-Serverteam/vote-bungee/workflows/Java%20CI/badge.svg)  
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Snapecraft-Serverteam/vote-bungee)
![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/Snapecraft-Serverteam/vote-bungee?include_prereleases&label=pre-release)
# vote-bungee

### Dependencies
- [NuVotifier](https://github.com/nuvotifier/NuVotifier)

### Config File

```YAML
SQL: # SQL login informations for sync
  host: localhost
  port: 3306
  user: root
  pw: ''
  database: vote
Vote:
  votecoinsonvote: 1000 # How many Votecoins a User gets per Vote

```

*Note:*
- - The Plugin creates needed tables into mysql
