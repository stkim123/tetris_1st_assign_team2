-- Only reserved username identities may be created while email confirmation is disabled.
-- Enable public.before_username_user_created in Auth > Hooks before changing that setting.
begin;

create or replace function public.before_username_user_created(event jsonb)
returns jsonb language plpgsql set search_path = '' as $$
declare
  v_email text := event->'user'->>'email';
begin
  if v_email ~ '^[a-z][a-z0-9_]{2,19}@players[.]campus-quest[.]invalid$'
      and event->'user'->'app_metadata'->>'provider' = 'email' then
    return '{}'::jsonb;
  end if;
  return jsonb_build_object('error', jsonb_build_object(
    'http_code', 403, 'message', 'Use a game ID to create an account.'));
end;
$$;

revoke all on function public.before_username_user_created(jsonb) from public, anon, authenticated;
grant usage on schema public to supabase_auth_admin;
grant execute on function public.before_username_user_created(jsonb) to supabase_auth_admin;

-- The hook protects creation. This trigger also reserves the namespace on email edits.
create or replace function public.keep_username_auth_email()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.email is distinct from new.email and
      (old.email ~ '^[a-z][a-z0-9_]{2,19}@players[.]campus-quest[.]invalid$' or
       new.email ~ '^[a-z][a-z0-9_]{2,19}@players[.]campus-quest[.]invalid$') then
    raise exception 'Username account email cannot change' using errcode = '23514';
  end if;
  return new;
end;
$$;

drop trigger if exists keep_username_auth_email_on_auth_user on auth.users;
create trigger keep_username_auth_email_on_auth_user before update of email on auth.users
for each row execute function public.keep_username_auth_email();

-- Ranked names must continue to reflect the Auth identity.
revoke update (display_name) on public.profiles from authenticated;
drop policy if exists profiles_own_update on public.profiles;

commit;
