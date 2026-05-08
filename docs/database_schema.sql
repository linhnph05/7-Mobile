-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.attachments (
  attachment_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  task_id bigint,
  uploader_id uuid,
  file_url text NOT NULL,
  file_name text,
  file_type text,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT attachments_pkey PRIMARY KEY (attachment_id),
  CONSTRAINT attachments_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(task_id)
);
CREATE TABLE public.comment_reactions (
  reaction_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  comment_id bigint,
  user_id uuid,
  reaction_type character varying NOT NULL,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT comment_reactions_pkey PRIMARY KEY (reaction_id),
  CONSTRAINT comment_reactions_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES public.comments(comment_id),
  CONSTRAINT comment_reactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.comments (
  comment_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  task_id bigint,
  user_id uuid,
  content text NOT NULL,
  created_at timestamp with time zone DEFAULT now(),
  is_deleted boolean NOT NULL DEFAULT false,
  CONSTRAINT comments_pkey PRIMARY KEY (comment_id),
  CONSTRAINT comments_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(task_id),
  CONSTRAINT comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.notifications (
  notification_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id uuid NOT NULL,
  actor_id uuid,
  type character varying NOT NULL,
  reference_id bigint,
  is_read boolean DEFAULT false,
  created_at timestamp with time zone DEFAULT now(),
  task_activity_id bigint,
  CONSTRAINT notifications_pkey PRIMARY KEY (notification_id),
  CONSTRAINT notifications_task_activity_id_fkey FOREIGN KEY (task_activity_id) REFERENCES public.task_activities(activity_id),
  CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id),
  CONSTRAINT notifications_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.project_activities (
  activity_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  project_id bigint NOT NULL,
  user_id uuid NOT NULL,
  entity_type character varying NOT NULL,
  entity_id bigint,
  action_type character varying NOT NULL,
  old_value jsonb,
  new_value jsonb,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT project_activities_pkey PRIMARY KEY (activity_id)
);
CREATE TABLE public.project_invitations (
  id uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  project_id bigint NOT NULL,
  inviter_id uuid NOT NULL,
  email text NOT NULL,
  role character varying NOT NULL DEFAULT 'MEMBER'::character varying,
  status character varying NOT NULL DEFAULT 'PENDING'::character varying,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  invitation_id bigint NOT NULL DEFAULT nextval('project_invitations_invitation_id_seq'::regclass),
  CONSTRAINT project_invitations_pkey PRIMARY KEY (invitation_id),
  CONSTRAINT project_invitations_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(project_id),
  CONSTRAINT project_invitations_inviter_id_fkey FOREIGN KEY (inviter_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.project_members (
  project_id bigint NOT NULL,
  user_id uuid NOT NULL,
  role text DEFAULT 'MEMBER'::text,
  joined_at timestamp with time zone DEFAULT now(),
  id bigint NOT NULL DEFAULT nextval('project_members_id_seq'::regclass),
  CONSTRAINT project_members_pkey PRIMARY KEY (id),
  CONSTRAINT project_members_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(project_id),
  CONSTRAINT project_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.projects (
  project_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  owner_id uuid,
  project_name text NOT NULL,
  description text,
  project_key character varying,
  background_color character varying,
  created_at timestamp with time zone DEFAULT now(),
  is_deleted boolean DEFAULT false,
  deleted_at timestamp with time zone,
  is_private boolean DEFAULT true,
  CONSTRAINT projects_pkey PRIMARY KEY (project_id),
  CONSTRAINT projects_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.task_activities (
  activity_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  task_id bigint,
  user_id uuid,
  action_type text NOT NULL,
  old_value text,
  new_value text,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT task_activities_pkey PRIMARY KEY (activity_id),
  CONSTRAINT task_activities_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(task_id),
  CONSTRAINT task_activities_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id)
);
CREATE TABLE public.task_dependencies (
  task_id bigint NOT NULL,
  depends_on_task_id bigint NOT NULL,
  CONSTRAINT task_dependencies_pkey PRIMARY KEY (task_id, depends_on_task_id),
  CONSTRAINT task_dependencies_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(task_id),
  CONSTRAINT task_dependencies_depends_on_task_id_fkey FOREIGN KEY (depends_on_task_id) REFERENCES public.tasks(task_id)
);
CREATE TABLE public.tasks (
  task_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  project_id bigint,
  title text NOT NULL,
  description text,
  status text DEFAULT 'TODO'::text,
  priority text DEFAULT 'MEDIUM'::text,
  position double precision DEFAULT 0,
  due_date timestamp with time zone,
  assignee_id uuid,
  parent_task_id bigint,
  created_at timestamp with time zone DEFAULT now(),
  start_date timestamp with time zone,
  tag text,
  updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  deadline_reminded_at timestamp with time zone,
  CONSTRAINT tasks_pkey PRIMARY KEY (task_id),
  CONSTRAINT tasks_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(project_id),
  CONSTRAINT tasks_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES public.users(user_id),
  CONSTRAINT tasks_parent_task_id_fkey FOREIGN KEY (parent_task_id) REFERENCES public.tasks(task_id)
);
CREATE TABLE public.users (
  user_id uuid NOT NULL DEFAULT gen_random_uuid(),
  email text NOT NULL UNIQUE,
  password_hash text,
  display_name text,
  bio text,
  avatar_url text,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT users_pkey PRIMARY KEY (user_id),
  CONSTRAINT users_auth_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);
CREATE TABLE public.work_logs (
  log_id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  task_id bigint,
  user_id uuid,
  start_time timestamp with time zone NOT NULL,
  end_time timestamp with time zone,
  duration_minutes integer DEFAULT 0,
  note text,
  created_at timestamp with time zone DEFAULT now(),
  remaining_minutes integer DEFAULT 0,
  CONSTRAINT work_logs_pkey PRIMARY KEY (log_id),
  CONSTRAINT work_logs_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(task_id),
  CONSTRAINT work_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id)
);

-- Table: user_devices - stores device tokens for push notifications
CREATE TABLE public.user_devices (
  id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id uuid NOT NULL,
  device_token text NOT NULL,
  platform text NOT NULL DEFAULT 'android',
  created_at timestamp with time zone DEFAULT now(),
  updated_at timestamp with time zone DEFAULT now(),
  CONSTRAINT user_devices_pkey PRIMARY KEY (id),
  CONSTRAINT user_devices_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS user_devices_user_token_idx ON public.user_devices (user_id, device_token);