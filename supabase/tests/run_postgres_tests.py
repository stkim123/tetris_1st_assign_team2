"""Run SQL and real transaction races against an isolated local PostgreSQL database.

Requires psycopg 3 and TETRIS_TEST_POSTGRES_DSN in the environment. The supplied
connection must have CREATEDB and point to a loopback PostgreSQL instance.
"""

import os
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Event
from time import sleep
from uuid import uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import conninfo_to_dict, make_conninfo


ROOT = Path(__file__).resolve().parents[2]
ADMIN = os.environ.get("TETRIS_TEST_POSTGRES_DSN")
if not ADMIN:
    raise SystemExit("Set TETRIS_TEST_POSTGRES_DSN for a local PostgreSQL admin database")
INFO = conninfo_to_dict(ADMIN)
if INFO.get("host") not in ("127.0.0.1", "localhost", "::1"):
    raise SystemExit("Concurrency tests require an explicit loopback PostgreSQL host")
NAME = "tetris_ranked_test_" + uuid4().hex[:12]
TEST = make_conninfo(ADMIN, dbname=NAME)


def call(name, *args):
    placeholders = ",".join(["%s"] * len(args))
    with psycopg.connect(TEST, autocommit=True) as conn:
        conn.execute("set statement_timeout = '10s'")
        return conn.execute("select public." + name + "(" + placeholders + ")", args).fetchone()[0]


def race_safe_call(name, *args):
    try:
        return ("ok", call(name, *args))
    except psycopg.Error as error:
        return ("error", error.sqlstate)


def race(first, second):
    gate = Barrier(2)

    def run(operation):
        gate.wait(timeout=10)
        try:
            return ("ok", operation())
        except psycopg.Error as error:
            return ("error", error.sqlstate)

    with ThreadPoolExecutor(max_workers=2) as pool:
        a = pool.submit(run, first)
        b = pool.submit(run, second)
        return a.result(timeout=20), b.result(timeout=20)


def ensure(condition, message):
    if not condition:
        raise AssertionError(message)


def run():
    with psycopg.connect(ADMIN, autocommit=True) as admin:
        admin.execute(sql.SQL("create database {}").format(sql.Identifier(NAME)))
    try:
        with psycopg.connect(TEST, autocommit=True) as conn:
            for filename in (
                "supabase/tests/bootstrap.sql",
                "supabase/migrations/202609290001_ranked_pvp.sql",
                "supabase/migrations/202610070001_username_profiles.sql",
                "supabase/migrations/202610070002_username_signup_policy.sql",
                "supabase/tests/ranking.sql",
            ):
                with conn.transaction():
                    conn.execute((ROOT / filename).read_text(encoding="utf-8"))
            print("PASS schema, permissions, sequential results")

            users = [uuid4() for _ in range(5)]
            with conn.transaction():
                for user in users:
                    conn.execute("insert into auth.users(id) values (%s)", (user,))
            a, b, c, d, e = users
            run_id = uuid4()
            match_id = uuid4()
            started = call("ranked_begin_match", match_id, run_id, "v1", a, b)
            ensure(started["status"] == "RUNNING", "begin failed")

            same = race(
                lambda: call("ranked_finish_match", match_id, run_id, a, "WIN"),
                lambda: call("ranked_finish_match", match_id, run_id, a, "WIN"),
            )
            ensure([item[0] for item in same] == ["ok", "ok"], "same result retry failed")
            ensure(all(item[1]["status"] == "FINALIZED" for item in same), "finalized result mismatch")
            counts = conn.execute("select games, rating from public.player_stats where user_id in (%s,%s)",
                                  (a, b)).fetchall()
            ensure(sorted(counts) == [(1, 984), (1, 1016)], "concurrent retry counted more than once")
            print("PASS concurrent duplicate finalization")

            duplicate_id = uuid4()
            duplicate = race(
                lambda: call("ranked_begin_match", duplicate_id, run_id, "v1", d, e),
                lambda: call("ranked_begin_match", duplicate_id, run_id, "v1", d, e),
            )
            ensure([item[0] for item in duplicate] == ["ok", "ok"], "duplicate begin failed")
            ensure(all(item[1]["match_id"] == str(duplicate_id) for item in duplicate),
                   "duplicate begin diverged")
            ensure(conn.execute("select count(*) from public.matches where match_id=%s",
                                (duplicate_id,)).fetchone()[0] == 1, "duplicate match row")
            call("ranked_void_match", duplicate_id, run_id, "CANCELLED")
            print("PASS concurrent duplicate start")

            match_ab, match_ac = uuid4(), uuid4()
            overlap = race(
                lambda: call("ranked_begin_match", match_ab, run_id, "v1", a, b),
                lambda: call("ranked_begin_match", match_ac, run_id, "v1", a, c),
            )
            ensure(sorted(item[0] for item in overlap) == ["error", "ok"],
                   "overlapping account started two matches")
            ensure(next(item[1] for item in overlap if item[0] == "error") == "PT409",
                   "overlap error code")
            winner_id = match_ab if overlap[0][0] == "ok" else match_ac
            finish_void = race(
                lambda: call("ranked_finish_match", winner_id, run_id, a, "WIN"),
                lambda: call("ranked_void_match", winner_id, run_id, "SERVER_ERROR"),
            )
            ensure(sorted(item[0] for item in finish_void) == ["error", "ok"],
                   "finish/void both succeeded")
            ensure(next(item[1] for item in finish_void if item[0] == "error") == "PT409",
                   "finish/void conflict code")
            actual = call("ranked_get_match", winner_id)
            ensure(actual["status"] in ("FINALIZED", "VOID"), "match remains unresolved")
            ensure(conn.execute("select count(*) from public.player_stats where user_id in (%s,%s,%s)"
                                " and active_match_id is not null", (a, b, c)).fetchone()[0] == 0,
                   "account lock leaked")
            expected_games = 2 if actual["status"] == "FINALIZED" else 1
            ensure(conn.execute("select games from public.player_stats where user_id=%s",
                                (a,)).fetchone()[0] == expected_games,
                   "race changed score incorrectly")
            print("PASS overlapping account and finish/void races")

            # A start that has written its match but has not committed holds the run
            # fence. Recovery must wait, then void that committed match atomically.
            pending_run, pending_match, finalized_match = uuid4(), uuid4(), uuid4()
            call("ranked_begin_match", finalized_match, pending_run, "v1", a, b)
            call("ranked_finish_match", finalized_match, pending_run, a, "WIN")
            entered, release = Event(), Event()

            def held_begin():
                with psycopg.connect(TEST, autocommit=True) as begin_conn:
                    begin_conn.execute("set statement_timeout = '10s'")
                    with begin_conn.transaction():
                        record = begin_conn.execute(
                            "select public.ranked_begin_match(%s,%s,%s,%s,%s)",
                            (pending_match, pending_run, "v1", d, e)).fetchone()[0]
                        entered.set()
                        ensure(release.wait(5), "held begin release timeout")
                        return record

            with ThreadPoolExecutor(max_workers=2) as pool:
                beginning = pool.submit(held_begin)
                ensure(entered.wait(5), "held begin did not register")
                recovering = pool.submit(call, "ranked_void_stopped_run", pending_run)
                sleep(0.15)
                ensure(not recovering.done(), "recovery crossed uncommitted begin")
                release.set()
                ensure(beginning.result(timeout=10)["status"] == "RUNNING", "held begin failed")
                ensure(recovering.result(timeout=10) == 1, "recover did not void late commit")
            ensure(call("ranked_get_match", pending_match)["status"] == "VOID", "late match survived")
            ensure(call("ranked_get_match", finalized_match)["status"] == "FINALIZED",
                   "recovery changed finalized result")
            ensure(conn.execute("select state from public.server_runs where run_id=%s",
                                (pending_run,)).fetchone()[0] == "STOPPED", "run fence not durable")
            print("PASS begin transaction before recovery fence")

            # Recovery that committed without a match must prohibit a late old-run
            # start. A concurrent begin waits for the uncommitted STOPPED marker.
            stopped_run, blocked_match = uuid4(), uuid4()
            stopped_ready, release_stop = Event(), Event()

            def held_recovery():
                with psycopg.connect(TEST, autocommit=True) as stop_conn:
                    stop_conn.execute("set statement_timeout = '10s'")
                    with stop_conn.transaction():
                        count = stop_conn.execute("select public.ranked_void_stopped_run(%s)",
                                                  (stopped_run,)).fetchone()[0]
                        stopped_ready.set()
                        ensure(release_stop.wait(5), "held recovery release timeout")
                        return count

            with ThreadPoolExecutor(max_workers=2) as pool:
                recovering = pool.submit(held_recovery)
                ensure(stopped_ready.wait(5), "held recovery did not fence")
                beginning = pool.submit(race_safe_call, "ranked_begin_match",
                                        blocked_match, stopped_run, "v1", d, e)
                sleep(0.15)
                ensure(not beginning.done(), "begin crossed uncommitted stopped run")
                release_stop.set()
                ensure(recovering.result(timeout=10) == 0, "absent recovery count")
                ensure(beginning.result(timeout=10) == ("error", "PT409"),
                       "late begin accepted after recovery")
            ensure(call("ranked_void_stopped_run", stopped_run) == 0,
                   "stopped run recovery changed on retry")
            print("PASS recovery before late begin fence")

            # Recovering several matches while another run starts with an
            # overlapping account must not deadlock or preserve the old games.
            old_run, new_run = uuid4(), uuid4()
            old_one, old_two, new_match = uuid4(), uuid4(), uuid4()
            call("ranked_begin_match", old_one, old_run, "v1", a, b)
            call("ranked_begin_match", old_two, old_run, "v1", c, d)
            cross = race(
                lambda: call("ranked_void_stopped_run", old_run),
                lambda: call("ranked_begin_match", new_match, new_run, "v1", a, c),
            )
            ensure(cross[0] == ("ok", 2), "old-run cross-match recovery failed")
            ensure(cross[1][0] == "ok" or cross[1] == ("error", "PT409"),
                   "unexpected overlapping begin outcome")
            ensure(call("ranked_get_match", old_one)["status"] == "VOID"
                   and call("ranked_get_match", old_two)["status"] == "VOID",
                   "cross-match recovery missed a game")
            if cross[1][0] == "ok":
                call("ranked_void_match", new_match, new_run, "CANCELLED")
            print("PASS cross-match begin/recovery lock order")

            finish_run, finish_match = uuid4(), uuid4()
            call("ranked_begin_match", finish_match, finish_run, "v1", b, e)
            finish_recover = race(
                lambda: call("ranked_finish_match", finish_match, finish_run, b, "WIN"),
                lambda: call("ranked_void_stopped_run", finish_run),
            )
            settled = call("ranked_get_match", finish_match)
            if settled["status"] == "FINALIZED":
                ensure(finish_recover[0][0] == "ok" and finish_recover[1] == ("ok", 0),
                       "recovery changed finalized result")
            else:
                ensure(settled["status"] == "VOID" and finish_recover[0] == ("error", "PT409")
                       and finish_recover[1] == ("ok", 1),
                       "finish escaped stopped-run void")
            print("PASS finish/recovery race preserves one terminal result")

            # Keep the pre-lease race suite above on its original RPC contract,
            # then exercise the upgrade and the durable admission fence.
            for filename in (
                "supabase/migrations/202610080001_ranked_run_lease.sql",
                "supabase/tests/run_lease.sql",
            ):
                with conn.transaction():
                    conn.execute((ROOT / filename).read_text(encoding="utf-8"))
            print("PASS run lease migration and restart fencing")
    finally:
        with psycopg.connect(ADMIN, autocommit=True) as admin:
            admin.execute(sql.SQL("drop database {}").format(sql.Identifier(NAME)))


if __name__ == "__main__":
    run()
