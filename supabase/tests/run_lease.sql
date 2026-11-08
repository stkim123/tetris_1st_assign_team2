-- Isolated DB order: bootstrap.sql -> pre-lease migrations -> ranking.sql ->
-- 202610080001_ranked_run_lease.sql -> this file.
set role authenticated;
do $$ begin
  begin
    perform public.ranked_run_lease('30000000-0000-4000-8000-000000000001', 'OPEN');
    raise exception 'client changed admission';
  exception when insufficient_privilege then null; end;
end $$;
reset role;

set role service_role;
do $$
declare v jsonb;
begin
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000001', 'CLAIM');
  if (v->>'owned')::boolean then raise exception 'initial policy opened'; end if;
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000001', 'OPEN');
  if not (v->>'owned')::boolean or not (v->>'enabled')::boolean then
    raise exception 'operator open failed'; end if;
  perform public.ranked_begin_match('40000000-0000-4000-8000-000000000001',
    '30000000-0000-4000-8000-000000000001', 'pvp-v1',
    '00000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000002');
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000002', 'CLAIM');
  if (v->>'owned')::boolean then raise exception 'overlapping run admitted'; end if;
end $$;
reset role;

-- Simulate a dead Render process by expiring the lease as DB owner.
update public.ranked_admission_control set lease_until = clock_timestamp() - interval '1 second';
set role service_role;
do $$
declare v jsonb;
begin
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000002', 'CLAIM');
  if not (v->>'owned')::boolean or not (v->>'enabled')::boolean then
    raise exception 'restart did not auto-open'; end if;
  if (select status from public.matches
      where match_id = '40000000-0000-4000-8000-000000000001') <> 'VOID' then
    raise exception 'old running match not reconciled'; end if;
  if exists (select 1 from public.player_stats where active_match_id =
      '40000000-0000-4000-8000-000000000001') then
    raise exception 'old participant remained active'; end if;
  begin
    perform public.ranked_begin_match('40000000-0000-4000-8000-000000000002',
      '30000000-0000-4000-8000-000000000001', 'pvp-v1',
      '00000000-0000-4000-8000-000000000001',
      '00000000-0000-4000-8000-000000000002');
    raise exception 'late begin accepted';
  exception when sqlstate 'PT409' then null; end;
  begin
    perform public.ranked_finish_match('40000000-0000-4000-8000-000000000001',
      '30000000-0000-4000-8000-000000000001',
      '00000000-0000-4000-8000-000000000001', 'WIN');
    raise exception 'late result accepted';
  exception when sqlstate 'PT409' then null; end;
  perform public.ranked_begin_match('40000000-0000-4000-8000-000000000003',
    '30000000-0000-4000-8000-000000000002', 'pvp-v1',
    '00000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000002');
  perform public.ranked_finish_match('40000000-0000-4000-8000-000000000003',
    '30000000-0000-4000-8000-000000000002',
    '00000000-0000-4000-8000-000000000001', 'WIN');
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000002', 'DRAIN');
  if (v->>'enabled')::boolean then raise exception 'drain failed'; end if;
end $$;
reset role;

update public.ranked_admission_control set lease_until = clock_timestamp() - interval '1 second';
set role service_role;
do $$
declare v jsonb;
begin
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000003', 'CLAIM');
  if (v->>'owned')::boolean or (v->>'enabled')::boolean then
    raise exception 'maintenance drain did not persist'; end if;
  v := public.ranked_run_lease('30000000-0000-4000-8000-000000000003', 'OPEN');
  if not (v->>'owned')::boolean or not (v->>'enabled')::boolean then
    raise exception 'operator could not reopen'; end if;
end $$;
reset role;

select 'PASS run lease SQL regression' as result;
