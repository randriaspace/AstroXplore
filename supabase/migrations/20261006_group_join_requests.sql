-- Migration for Group Join Requests (QR Code Scan & Approval Pipeline)

CREATE TABLE IF NOT EXISTS public.group_join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL
);

-- Enable RLS
ALTER TABLE public.group_join_requests ENABLE ROW LEVEL SECURITY;

-- Applicants and Club Admins can read join requests
CREATE POLICY read_own_join_requests ON public.group_join_requests
    FOR SELECT TO authenticated
    USING (user_id = auth.uid() OR private.is_group_admin(group_id));

-- Submit request RPC
CREATE OR REPLACE FUNCTION public.submit_group_join_request(p_group_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_request_id UUID;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    -- If already member, no request needed
    IF EXISTS (
        SELECT 1 FROM public.group_members WHERE group_id = p_group_id AND user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'User is already a member of this club' USING ERRCODE = '23505';
    END IF;

    INSERT INTO public.group_join_requests (group_id, user_id, status)
    VALUES (p_group_id, auth.uid(), 'pending')
    RETURNING id INTO v_request_id;

    RETURN v_request_id;
END;
$$;

-- Approve request RPC
CREATE OR REPLACE FUNCTION public.approve_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_req public.group_join_requests%ROWTYPE;
BEGIN
    SELECT * INTO v_req FROM public.group_join_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found' USING ERRCODE = '22023';
    END IF;

    IF NOT private.is_group_admin(v_req.group_id) THEN
        RAISE EXCEPTION 'Not authorized to approve requests for this club' USING ERRCODE = '42501';
    END IF;

    -- Add to members
    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_req.group_id, v_req.user_id, 'member')
    ON CONFLICT (group_id, user_id) DO NOTHING;

    -- Update request status
    UPDATE public.group_join_requests
    SET status = 'accepted', reviewed_at = NOW(), reviewed_by = auth.uid()
    WHERE id = p_request_id;

    RETURN TRUE;
END;
$$;

-- Decline request RPC
CREATE OR REPLACE FUNCTION public.decline_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_req public.group_join_requests%ROWTYPE;
BEGIN
    SELECT * INTO v_req FROM public.group_join_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Request not found' USING ERRCODE = '22023';
    END IF;

    IF NOT private.is_group_admin(v_req.group_id) THEN
        RAISE EXCEPTION 'Not authorized to decline requests for this club' USING ERRCODE = '42501';
    END IF;

    UPDATE public.group_join_requests
    SET status = 'rejected', reviewed_at = NOW(), reviewed_by = auth.uid()
    WHERE id = p_request_id;

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.submit_group_join_request(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.approve_group_join_request(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.decline_group_join_request(UUID) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.submit_group_join_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.approve_group_join_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.decline_group_join_request(UUID) TO authenticated;
