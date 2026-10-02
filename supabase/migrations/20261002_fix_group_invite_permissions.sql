-- Ensure the private invite token RPC exists and is available to authenticated club admins
CREATE OR REPLACE FUNCTION public.create_group_invite(
    p_group_id UUID,
    p_expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '7 days',
    p_max_uses INTEGER DEFAULT 1
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_token TEXT;
BEGIN
    IF auth.uid() IS NULL OR NOT public.is_group_admin(p_group_id) THEN
        RAISE EXCEPTION 'Not authorized to create an invite' USING ERRCODE = '42501';
    END IF;

    IF p_expires_at <= NOW() OR p_expires_at > NOW() + INTERVAL '90 days' THEN
        RAISE EXCEPTION 'Invite expiry must be within the next 90 days' USING ERRCODE = '22023';
    END IF;

    IF p_max_uses < 1 OR p_max_uses > 500 THEN
        RAISE EXCEPTION 'Invite max uses must be between 1 and 500' USING ERRCODE = '22023';
    END IF;

    v_token := encode(extensions.gen_random_bytes(32), 'hex');
    INSERT INTO public.group_invites (group_id, token_hash, created_by, expires_at, max_uses)
    VALUES (p_group_id, extensions.digest(convert_to(v_token, 'UTF8'), 'sha256'), auth.uid(), p_expires_at, p_max_uses);

    RETURN v_token;
END;
$$;

REVOKE ALL ON FUNCTION public.create_group_invite(UUID, TIMESTAMPTZ, INTEGER) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_group_invite(UUID, TIMESTAMPTZ, INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION public.accept_group_invite(p_token TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_invite public.group_invites%ROWTYPE;
    v_inserted INTEGER;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    IF p_token IS NULL OR p_token !~ '^[0-9a-fA-F]{64}$' THEN
        RAISE EXCEPTION 'Invalid invite' USING ERRCODE = '22023';
    END IF;

    SELECT * INTO v_invite
    FROM public.group_invites AS gi
    WHERE gi.token_hash = extensions.digest(convert_to(lower(p_token), 'UTF8'), 'sha256')
    FOR UPDATE;

    IF NOT FOUND
       OR v_invite.revoked_at IS NOT NULL
       OR v_invite.expires_at <= NOW()
       OR v_invite.uses_count >= v_invite.max_uses THEN
        RAISE EXCEPTION 'Invite is invalid, expired, revoked, or already used' USING ERRCODE = '22023';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = v_invite.group_id AND gm.user_id = auth.uid()
    ) THEN
        RETURN v_invite.group_id;
    END IF;

    INSERT INTO public.group_members (group_id, user_id, role)
    SELECT v_invite.group_id, auth.uid(), 'member'
    WHERE EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = v_invite.group_id AND g.status = 'active'
    )
    ON CONFLICT (group_id, user_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;
    IF v_inserted = 0 THEN
        RAISE EXCEPTION 'Club is not active or membership could not be created' USING ERRCODE = '23514';
    END IF;

    UPDATE public.group_invites
    SET uses_count = uses_count + 1
    WHERE id = v_invite.id;

    RETURN v_invite.group_id;
END;
$$;

REVOKE ALL ON FUNCTION public.accept_group_invite(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_group_invite(TEXT) TO authenticated;
