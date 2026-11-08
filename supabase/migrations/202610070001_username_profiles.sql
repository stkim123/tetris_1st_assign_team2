-- Show an ID-based account's canonical username in PvP rankings.
-- The value comes from the reserved Auth email namespace, not editable metadata.
begin;

create or replace function public.create_ranked_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_name text;
begin
  v_name := case
    when new.email ~ '^[a-z][a-z0-9_]{2,19}@players[.]campus-quest[.]invalid$'
      then split_part(new.email, '@', 1)
    else 'Player ' || left(new.id::text, 8)
  end;
  insert into public.profiles(user_id, display_name)
  values (new.id, v_name) on conflict do nothing;
  insert into public.player_stats(user_id) values (new.id) on conflict do nothing;
  return new;
end;
$$;

commit;
