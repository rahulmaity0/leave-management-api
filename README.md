# Leave Management API

A Spring Boot REST API for handling employee leave requests. An employee applies for
leave, a manager approves or rejects it, and the system keeps track of how many days
everyone has left.

I started this as a plain employee CRUD app and then built the leave workflow on top,
because CRUD on its own doesn't really have any decisions in it. The leave part does —
you have to work out whether dates clash, whether someone still has enough balance,
and who is actually allowed to approve what.

## The rules it enforces

This is the part I spent most of the time on:

- You can't apply for leave that overlaps leave you already have booked. Only pending
  and approved requests block the dates — if a request was rejected or cancelled, those
  dates are free again.
- You can't ask for more days than you have left, unless it's unpaid leave.
- The balance is only deducted when the leave is actually approved, not when you apply.
- It's checked a second time at approval, because you might have had other leave
  approved in the meantime that ate into the balance.
- You can't approve your own leave request. Someone else has to.
- Only a pending request can be approved or rejected. Once it's been decided, trying
  again gets you a 409 instead of quietly overwriting the earlier decision.
- You can cancel your own pending request any time. You can cancel approved leave too,
  but only before it starts — and then the days go back on your balance.

## Running it

You need Java 17+ and a Postgres database. Create a database called `employee_db`, then
set your password as an environment variable so it isn't sitting in the config file:

```
set DB_PASSWORD=yourpassword
./mvnw spring-boot:run
```

`DB_URL` and `DB_USERNAME` can be overridden the same way if your setup is different.

Hibernate creates the tables on startup.

One thing to watch out for if you already had employee rows in the database from
before: the `leave_balance` column gets added as null for them, and that blows up when
it's read back into an int field. Run this once and you're fine:

```sql
UPDATE employeemodel SET leave_balance = 20 WHERE leave_balance IS NULL;
```

## Endpoints

Employees (this part is basic CRUD):

```
GET    /employees
GET    /employees/{id}
POST   /employees
DELETE /employees/{id}
```

Leave:

```
POST   /leaves                        apply for leave
GET    /leaves/{id}                   one request
GET    /leaves?status=PENDING         the approval queue, oldest first
GET    /leaves/employee/{id}          one person's leave history
PATCH  /leaves/{id}/approve           manager approves
PATCH  /leaves/{id}/reject            manager rejects
PATCH  /leaves/{id}/cancel?employeeId=1   employee withdraws it
```

Applying looks like this:

```json
POST /leaves
{
  "employeeId": 1,
  "startDate": "2026-10-10",
  "endDate": "2026-10-15",
  "type": "CASUAL",
  "reason": "family function"
}
```

`type` is CASUAL, SICK, EARNED or UNPAID. Status goes PENDING first, then APPROVED,
REJECTED or CANCELLED.

Errors all come back in the same shape, with the status code matching what actually
went wrong — 404 if the id doesn't exist, 400 if the request breaks a rule, 409 if it
clashes with something that's already there:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Employee already has a pending or approved leave overlapping these dates",
  "timestamp": "2026-09-01T02:05:34"
}
```

Validation errors also tell you which field was wrong instead of just saying the
request was bad.

## Tests

```
./mvnw test
```

64 tests, and they run against in-memory H2, so you don't need Postgres running to
execute them. There are three kinds:

`LeaveServiceTest` covers the business rules with Mockito mocks, no Spring context at
all, so it's fast. It's grouped with `@Nested` classes per operation, and the balance
check is a parameterised test that walks the boundary — with 5 days left, asking for 5
has to work and asking for 6 has to fail. That's where off-by-one bugs in the day
counting would show up.

`LeaveRequestRepositoryTest` uses `@DataJpaTest` against H2 to test the actual SQL. The
overlap query gets a full boundary table — nine cases around the edges of a booked
10–15 October leave. The important ones are that the 9th and the 16th don't clash but
the 10th and the 15th do, since that's exactly where you'd get an inclusive/exclusive
mix-up.

`LeaveControllerTest` uses `@WebMvcTest` with the service mocked out, and only checks
the HTTP side — routing, validation, status codes, and the JSON that comes back.

The reason they're split like that is so a failure tells you where the problem is. If
a rule is wrong only the service test fails. If the SQL is wrong only the repository
test fails.

## Built with

Spring Boot 4.1.1, Spring Data JPA, Postgres, Bean Validation, JUnit 5, Mockito,
AssertJ, H2 for tests. No Lombok — the getters and setters are written out.

The DTOs are Java records, which is why you'll see `request.employeeId()` rather than
`getEmployeeId()`.

## Still to do

- No authentication yet. That's why approve and cancel take an id in the request —
  once there's JWT login, that comes from the logged-in user instead and the
  "you can't approve your own leave" check gets a lot more meaningful.
- Half-day leave.
- Public holidays and weekends currently count as leave days.
- Docker and a CI workflow.
- The older employee classes use lowercase names (`employeemodel`, `employeeservice`)
  from when I first wrote them. The leave classes follow normal Java naming.
