import configparser
from functools import lru_cache
from pathlib import Path

from fastapi import Depends

from .client_base import ClientBaseClient
from .config import Settings, get_settings


@lru_cache
def load_field_mapping() -> dict[str, str]:
    parser = configparser.ConfigParser()
    path = Path(__file__).with_name("field_mapping.ini")
    parser.read(path, encoding="utf-8")
    return dict(parser["application"])


def get_client_base(settings: Settings = Depends(get_settings)) -> ClientBaseClient:
    return ClientBaseClient(settings, load_field_mapping())

