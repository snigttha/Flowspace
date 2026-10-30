# FlowSpace Test Plan

## Cycle tracker

- Validate that cycle starts cannot be in the future and lengths are limited to 15 through 60 days.
- Verify no prediction is returned for zero logs and the entered length is used for one log.
- Verify two- and five-log predictions average the gaps between consecutive start dates, round half days up, and add the bounded average to the latest start date.
- Verify cycle rows are returned in date order and a second user cannot list or update the first user's rows.
- Verify posting an already logged start date updates its length without creating a duplicate.
- Verify the wellness calendar marks logged starts and the server-computed predicted date, including month navigation.
- Verify unauthenticated cycle requests return 401 and invalid dates or lengths return 400 with a field-level message.

## Regression and privacy

- Run the full Maven test suite after the backend changes.
- Exercise register, login, cycle create/update, and logout using two accounts; confirm neither account can observe the other's cycle dates or prediction.
- Check the wellness page at 375 px, 768 px, and 1280 px and confirm keyboard focus and the two calendar markers remain distinguishable without relying on color alone.

## Prediction storage decision

Predictions are computed from each user's ordered `period_logs` on request. The existing nullable `predicted_next_date` column is left unused so a new log or edited cycle length cannot leave a stale prediction behind.